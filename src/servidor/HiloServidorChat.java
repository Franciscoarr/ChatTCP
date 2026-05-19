package servidor;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class HiloServidorChat extends Thread {

    private final Socket socket;
    private InfoHilos infoSala;
    private BufferedReader entrada;
    private PrintWriter salida;
    private String nombreCliente;
    private String rolCliente;
    private String nombreSala;

    public HiloServidorChat(Socket socket) {
        this.socket = socket;
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {}
    }

    @Override
    public void run() {
        try {
            // 1. SISTEMA DE LOGIN Y REGISTRO (Recibe: ACCION###Nick###Password)
            String loginData = entrada.readLine();
            nombreSala = entrada.readLine();

            if (loginData == null || !loginData.contains("###")) { socket.close(); return; }

            String[] credenciales = loginData.split("###");
            if (credenciales.length < 3) { socket.close(); return; }

            String accion = credenciales[0]; // LOGIN o REGISTER
            nombreCliente = credenciales[1];
            String password = credenciales[2];
            String ipCliente = socket.getInetAddress().getHostAddress();

            // 2. Lógica de Acción (Registrar o Validar)
            if (accion.equals("REGISTER")) {
                String resultado = GestorSeguridad.registrarUsuario(nombreCliente, password);
                if (resultado.equals("EXISTS")) {
                    salida.println("###ERROR-LOGIN###El nickname ya existe. Inicia sesión o elige otro.");
                    socket.close(); return;
                } else if (resultado.equals("OK")) {
                    rolCliente = "ORDINARIO"; // Por defecto
                } else {
                    salida.println("###ERROR-LOGIN###Error al guardar en el servidor.");
                    socket.close(); return;
                }
            }
            else if (accion.equals("LOGIN")) {
                rolCliente = GestorSeguridad.validarUsuario(ipCliente, nombreCliente, password);

                if (rolCliente == null) {
                    salida.println("###ERROR-LOGIN###Credenciales incorrectas");
                    socket.close(); return;
                } else if (rolCliente.equals("BLOQUEADO")) {
                    salida.println("###ERROR-LOGIN###Demasiados intentos. IP Bloqueada.");
                    socket.close(); return;
                }
            }

            // Comprobar si ya estaba conectado
            if (ServidorChat.nombresUsuarios.containsValue(nombreCliente)) {
                salida.println("###ERROR-LOGIN###El usuario ya está conectado.");
                socket.close(); return;
            }

            // --- Éxito al conectar ---
            salida.println("###LOGIN-OK###" + rolCliente);
            this.infoSala = ServidorChat.mapaSalas.getOrDefault(nombreSala, ServidorChat.mapaSalas.get("#General"));

            synchronized (infoSala) {
                if (infoSala.addSocket(socket)) {
                    infoSala.setActuales(infoSala.getActuales() + 1);
                    ServidorChat.nombresUsuarios.put(socket, nombreCliente);
                    ServidorChat.rolesUsuarios.put(nombreCliente, rolCliente);
                    ServidorChat.registrarLog(nombreCliente + " conectado a " + nombreSala + " (" + accion + ")");
                } else {
                    salida.println("###ERROR-LOGIN###Sala llena");
                    socket.close(); return;
                }
            }

            enviarMensajesASala("###PARSER-ENTRA###" + nombreCliente);
            enviarMensajesASala("> " + nombreCliente + " (" + rolCliente + ") ha entrado en " + nombreSala);

            Socket[] tabla = infoSala.getTabla();
            for (Socket s : tabla) {
                if (s != null && !s.isClosed() && s != socket) {
                    String otro = ServidorChat.nombresUsuarios.get(s);
                    if (otro != null) salida.println("###PARSER-ENTRA###" + otro);
                }
            }

            // 3. BUCLE PRINCIPAL DE COMANDOS
            String texto;
            while ((texto = entrada.readLine()) != null) {
                if (texto.equals("*****")) break;

                // Suspensión: Bloquea hablar a menos que seas moderador
                if (infoSala.isSuspendido() && !ServidorChat.rolesUsuarios.get(nombreCliente).equals("MODERADOR") && !texto.startsWith("/")) {
                    salida.println("> Sistema: El canal está suspendido por un moderador.");
                    continue;
                }

                if (texto.startsWith("/")) procesarComandos(texto);
                else enviarMensajesASala(nombreCliente + "> " + texto);
            }
        } catch (IOException e) {
            ServidorChat.registrarLog("Desconexión abrupta de " + nombreCliente);
        } finally {
            finalizarConexion();
        }
    }

    private void procesarComandos(String texto) {
        String[] partes = texto.split(" ", 3);
        String comando = partes[0].toLowerCase();

        // Verificamos el rol directamente desde el mapa central (por si nos han hecho promote)
        boolean esMod = ServidorChat.rolesUsuarios.get(nombreCliente).equals("MODERADOR");

        // --- COMANDOS PARA TODOS ---

        if (comando.equals("/fileall")) {
            String payloadInfo = texto.substring("/fileall ".length());
            // Se envía a la sala con el formato normal "Nick> Mensaje"
            // Así, el moderador podrá borrarlo usando el /delmsg
            enviarMensajesASala(nombreCliente + "> ha compartido un archivo: " + payloadInfo);
            return;
        }

        // Ayuda
        if (comando.equals("/help")) {
            salida.println("> Sistema (Ayuda): Comandos básicos -> /privado [nick] [msg], /delmsg, /clear");
            if (esMod) {
                salida.println("> Sistema (Mod): /kick [nick], /suspend, /promote [nick] [segs], /delmsg [nick]");
            }
            return;
        }

        // Mensajes Privados y Archivos
        if (comando.equals("/privado") || comando.equals("/file")) {
            if (partes.length < 3) return;
            String destino = partes[1];
            String payloadCifrado = partes[2];

            for (Map.Entry<Socket, String> entry : ServidorChat.nombresUsuarios.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(destino)) {
                    try {
                        PrintWriter out = new PrintWriter(entry.getKey().getOutputStream(), true);
                        String prefijo = comando.equals("/file") ? "[ARCHIVO PRIVADO de " : "[PRIVADO de ";
                        out.println(prefijo + nombreCliente + "]: " + payloadCifrado);
                        salida.println("[Enviado a " + destino + "]: " + payloadCifrado);
                    } catch (IOException _) {}
                }
            }
        }

        // Borrar Mensajes (Dual: Propio vs Otros)
        else if (comando.equals("/delmsg")) {
            String targetNick = nombreCliente; // Por defecto se borra el suyo propio

            // Si intenta especificar un nombre para borrar el de otro...
            if (partes.length >= 2) {
                if (esMod) {
                    targetNick = partes[1]; // Si es moderador, le dejamos apuntar a otro
                } else {
                    salida.println("> Sistema: Error. Solo un Moderador puede borrar los mensajes de otra persona.");
                    return; // Abortamos
                }
            }

            // Avisamos a todos los clientes que borren el último mensaje de 'targetNick'
            for (Socket s : ServidorChat.nombresUsuarios.keySet()) {
                if (s != null && !s.isClosed()) {
                    try { new PrintWriter(s.getOutputStream(), true).println("###DEL-LAST###" + targetNick); } catch (IOException e) {}
                }
            }

            // Modificamos el historial guardado en el servidor
            String history = infoSala.getMensajes();
            String[] lines = history.split("\n");
            StringBuilder newHistory = new StringBuilder();
            boolean deleted = false;
            for (int i = lines.length - 1; i >= 0; i--) {
                if (!deleted && lines[i].startsWith(targetNick + "> ")) {
                    newHistory.insert(0, ">> Mensaje eliminado <<\n");
                    deleted = true;
                } else if (!lines[i].trim().isEmpty()) {
                    newHistory.insert(0, lines[i] + "\n");
                }
            }
            infoSala.setMensajes(newHistory.toString());

            // Si el moderador ha borrado el de otra persona, dejamos constancia en el chat
            if (!targetNick.equals(nombreCliente)) {
                enviarMensajesASala("> Sistema: Un Moderador ha suprimido un mensaje de " + targetNick);
            }
        }

        // --- COMANDOS EXCLUSIVOS DE MODERADOR ---
        else if (esMod) {
            if (comando.equals("/kick") && partes.length >= 2) {
                String target = partes[1];
                enviarMensajesASala("> Sistema: " + target + " ha sido EXPULSADO por el moderador.");
                for (Map.Entry<Socket, String> entry : ServidorChat.nombresUsuarios.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(target)) {
                        try { new PrintWriter(entry.getKey().getOutputStream(), true).println("###KICKED###"); } catch (IOException e) {}
                    }
                }
            }
            else if (comando.equals("/suspend")) {
                infoSala.setSuspendido(!infoSala.isSuspendido());
                enviarMensajesASala("> Sistema: El moderador ha " + (infoSala.isSuspendido() ? "SUSPENDIDO" : "REANUDADO") + " el canal.");
            }
            else if (comando.equals("/promote") && partes.length >= 3) {
                String target = partes[1];
                int tiempoSecs = Integer.parseInt(partes[2]);
                ServidorChat.rolesUsuarios.put(target, "MODERADOR");
                enviarMensajesASala("> Sistema: " + target + " ha sido ascendido a MODERADOR temporalmente.");

                // Temporizador para devolverlo a la normalidad
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (ServidorChat.rolesUsuarios.containsKey(target)) {
                            ServidorChat.rolesUsuarios.put(target, "ORDINARIO");
                            enviarMensajesASala("> Sistema: Los privilegios de " + target + " han expirado.");
                        }
                    }
                }, tiempoSecs * 1000L);
            }
        } else {
            salida.println("> Sistema: No tienes permisos de Moderador para usar este comando.");
        }
    }

    private void enviarMensajesASala(String txt) {
        Socket[] tabla = infoSala.getTabla();
        for (Socket s : tabla) {
            if (s != null && !s.isClosed()) {
                try { new PrintWriter(s.getOutputStream(), true).println(txt); } catch (IOException _) {}
            }
        }
    }

    private void finalizarConexion() {
        if (infoSala != null && nombreCliente != null) {
            enviarMensajesASala("> " + nombreCliente + " ha abandonado el canal");
            enviarMensajesASala("###PARSER-SALE###" + nombreCliente);
            synchronized (infoSala) { infoSala.setActuales(infoSala.getActuales() - 1); }
            ServidorChat.registrarLog(nombreCliente + " desconectado.");
        }
        ServidorChat.nombresUsuarios.remove(socket);
        ServidorChat.rolesUsuarios.remove(nombreCliente);
        try { socket.close(); } catch (IOException _) {}
    }
}