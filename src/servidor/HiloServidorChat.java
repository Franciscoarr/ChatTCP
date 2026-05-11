package servidor;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Cada cliente que se conecta genera una instancia de este Hilo
 * Esta clase atiende de manera concurrente a ese cliente en concreto
 */
public class HiloServidorChat extends Thread {

    private final Socket socket;
    private InfoHilos infoSala; // Referencia compartida a la sala donde está el cliente
    private BufferedReader entrada;
    private PrintWriter salida;
    private String nombreCliente;
    private String rolCliente;
    private String nombreSala;

    public HiloServidorChat(Socket socket) {
        this.socket = socket;
        try {
            // Inicializa flujos de lectura y escritura del socket
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {}
    }

    @Override
    public void run() {
        try {
            // 1. SISTEMA DE LOGIN Y REGISTRO
            // El cliente envía su acción en formato: ACCION###Nick###Password
            String loginData = entrada.readLine();
            nombreSala = entrada.readLine();

            if (loginData == null || !loginData.contains("###")) { socket.close(); return; }

            String[] credenciales = loginData.split("###");
            if (credenciales.length < 3) { socket.close(); return; }

            String accion = credenciales[0]; // Puede ser LOGIN o REGISTER
            nombreCliente = credenciales[1];
            String password = credenciales[2];
            String ipCliente = socket.getInetAddress().getHostAddress();

            // 2. PROCESAMIENTO DE AUTENTICACIÓN
            if (accion.equals("REGISTER")) {
                String resultado = GestorSeguridad.registrarUsuario(nombreCliente, password);
                if (resultado.equals("EXISTS")) {
                    salida.println("###ERROR-LOGIN###El nickname ya existe. Inicia sesión o elige otro.");
                    socket.close(); return;
                } else if (resultado.equals("OK")) {
                    rolCliente = "ORDINARIO"; // Recién registrado es ordinario siempre
                } else {
                    salida.println("###ERROR-LOGIN###Error al guardar en el servidor.");
                    socket.close(); return;
                }
            }
            else if (accion.equals("LOGIN")) {
                // Verificamos credenciales con el Gestor
                rolCliente = GestorSeguridad.validarUsuario(ipCliente, nombreCliente, password);

                if (rolCliente == null) {
                    // Contraseña incorrecta: Informamos de los intentos restantes
                    int restantes = GestorSeguridad.getIntentosRestantes(ipCliente);
                    salida.println("###ERROR-LOGIN###Credenciales incorrectas. Te quedan " + restantes + " intento(s).");
                    socket.close(); return;
                } else if (rolCliente.equals("BLOQUEADO")) {
                    salida.println("###ERROR-LOGIN###Demasiados intentos. IP Bloqueada.");
                    socket.close(); return;
                }
            }

            // Comprobar que no haya iniciado sesión dos veces (sesión duplicada activa)
            if (ServidorChat.nombresUsuarios.containsValue(nombreCliente)) {
                salida.println("###ERROR-LOGIN###El usuario ya está conectado.");
                socket.close(); return;
            }

            // --- Si llega hasta aquí, EL ACCESO ES CORRECTO ---
            salida.println("###LOGIN-OK###" + rolCliente);
            // Recuperamos el objeto compartido de la sala correspondiente
            this.infoSala = ServidorChat.mapaSalas.getOrDefault(nombreSala, ServidorChat.mapaSalas.get("#General"));

            // Añadimos el socket al array de la sala de forma segura (synchronized)
            synchronized (infoSala) {
                if (infoSala.addSocket(socket)) {
                    infoSala.setActuales(infoSala.getActuales() + 1);
                    // Lo registramos en los mapas globales
                    ServidorChat.nombresUsuarios.put(socket, nombreCliente);
                    ServidorChat.rolesUsuarios.put(nombreCliente, rolCliente);
                    ServidorChat.registrarLog(nombreCliente + " conectado a " + nombreSala + " (" + accion + ")");
                } else {
                    salida.println("###ERROR-LOGIN###Sala llena");
                    socket.close(); return;
                }
            }

            // --- ACTUALIZACIÓN DE PIZARRAS Y LISTAS ---
            // Le decimos a los demás que este usuario ha entrado
            enviarMensajesASala("###PARSER-ENTRA###" + nombreCliente);
            enviarMensajesASala("> " + nombreCliente + " (" + rolCliente + ") ha entrado en " + nombreSala);

            // Rellenamos la lista derecha del usuario con la gente que ya estaba
            Socket[] tabla = infoSala.getTabla();
            for (Socket s : tabla) {
                if (s != null && !s.isClosed() && s != socket) {
                    String otro = ServidorChat.nombresUsuarios.get(s);
                    if (otro != null) salida.println("###PARSER-ENTRA###" + otro);
                }
            }

            // 3. BUCLE PRINCIPAL: Escucha constante de mensajes
            String texto;
            while ((texto = entrada.readLine()) != null) {
                if (texto.equals("*****")) break; // String de finalización

                // Control de Suspensión: Si el canal está suspendido, no deja enviar mensajes normales
                // Permite usar comandos porque empiezan por '/' (ej. para que el mod pueda des-suspender)
                if (infoSala.isSuspendido() && !texto.startsWith("/")) {
                    salida.println("> Sistema: El canal está actualmente SUSPENDIDO. Nadie puede escribir.");
                    continue; // Salta la iteración, ignorando el mensaje del usuario
                }

                // Desvío lógico: ¿Es un comando (/) o un mensaje de texto normal?
                if (texto.startsWith("/")) procesarComandos(texto);
                else enviarMensajesASala(nombreCliente + "> " + texto);
            }
        } catch (IOException e) {
            ServidorChat.registrarLog("Desconexión abrupta de " + nombreCliente);
        } finally {
            finalizarConexion(); // Limpia sockets y variables al salir
        }
    }

    /**
     * Interpreta y ejecuta las funciones especiales del chat (Privados y Moderación)
     */
    private void procesarComandos(String texto) {
        String[] partes = texto.split(" ", 3);
        String comando = partes[0].toLowerCase();

        // Verificamos si tiene el rol de moderador en tiempo real
        boolean esMod = ServidorChat.rolesUsuarios.get(nombreCliente).equals("MODERADOR");

        // --- 1. COMANDOS GENERALES (Todos pueden usarlos) ---

        // Comando Privado o Envío de Archivo (Enrutamiento punto a punto)
        if (comando.equals("/privado") || comando.equals("/file")) {
            if (partes.length < 3) return;
            String destino = partes[1];
            String payloadCifrado = partes[2];

            // Busca al destinatario en el mapa general y le envía el contenido por su socket
            for (Map.Entry<Socket, String> entry : ServidorChat.nombresUsuarios.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(destino)) {
                    try {
                        PrintWriter out = new PrintWriter(entry.getKey().getOutputStream(), true);
                        String prefijo = comando.equals("/file") ? "[ARCHIVO PRIVADO de " : "[PRIVADO de ";
                        out.println(prefijo + nombreCliente + "]: " + payloadCifrado);

                        // Confirmación al emisor
                        salida.println("[Enviado a " + destino + "]: " + payloadCifrado);
                    } catch (IOException _) {}
                }
            }
        }
        // Comando Borrar Mensaje
        else if (comando.equals("/delmsg")) {
            String targetNick = nombreCliente; // Por defecto intentas borrar el tuyo

            // Si se pasa un argumento extra (ej: /delmsg OtroUser)
            if (partes.length >= 2) {
                if (esMod) targetNick = partes[1]; // El Mod puede apuntar a otra persona
                else {
                    salida.println("> Sistema: Error. Solo un Moderador puede borrar los mensajes de otra persona.");
                    return; // Si no es Mod, no hace nada
                }
            }

            // 1. Enviar orden a todos los clientes para que borren visualmente el último mensaje de targetNick
            for (Socket s : ServidorChat.nombresUsuarios.keySet()) {
                if (s != null && !s.isClosed()) {
                    try { new PrintWriter(s.getOutputStream(), true).println("###DEL-LAST###" + targetNick); } catch (IOException e) {}
                }
            }

            // 2. Alterar el historial en memoria para borrar las pruebas permanentemente
            String history = infoSala.getMensajes();
            String[] lines = history.split("\n");
            StringBuilder newHistory = new StringBuilder();
            boolean deleted = false;
            // Busca de abajo a arriba el último mensaje y lo sobrescribe
            for (int i = lines.length - 1; i >= 0; i--) {
                if (!deleted && lines[i].startsWith(targetNick + "> ")) {
                    newHistory.insert(0, ">> Mensaje eliminado <<\n");
                    deleted = true;
                } else if (!lines[i].trim().isEmpty()) {
                    newHistory.insert(0, lines[i] + "\n");
                }
            }
            infoSala.setMensajes(newHistory.toString());

            // Aviso público si un mod intervino los mensajes de un usuario
            if (!targetNick.equals(nombreCliente)) {
                enviarMensajesASala("> Sistema: Un Moderador ha suprimido un mensaje de " + targetNick);
            }
        }

        // --- 2. COMANDOS DE MODERADOR (Privilegiados) ---
        else if (esMod) {
            // Expulsar a un usuario conectando el socket remotamente
            if (comando.equals("/kick") && partes.length >= 2) {
                String target = partes[1];
                enviarMensajesASala("> Sistema: " + target + " ha sido EXPULSADO por el moderador.");
                for (Map.Entry<Socket, String> entry : ServidorChat.nombresUsuarios.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(target)) {
                        try { new PrintWriter(entry.getKey().getOutputStream(), true).println("###KICKED###"); } catch (IOException e) {}
                    }
                }
            }
            // Bloquear el envío de mensajes en toda la sala
            else if (comando.equals("/suspend")) {
                infoSala.setSuspendido(!infoSala.isSuspendido());
                enviarMensajesASala("> Sistema: El moderador ha " + (infoSala.isSuspendido() ? "SUSPENDIDO" : "REANUDADO") + " el canal.");
            }
            // Dar permisos de Moderador temporales a un usuario ordinario
            else if (comando.equals("/promote") && partes.length >= 3) {
                String target = partes[1];
                int tiempoSecs = Integer.parseInt(partes[2]);
                ServidorChat.rolesUsuarios.put(target, "MODERADOR"); // Cambia el mapa
                enviarMensajesASala("> Sistema: " + target + " ha sido ascendido a MODERADOR temporalmente.");

                // Programa una tarea (TimerTask) que se ejecutará en X segundos para quitarle el rol
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (ServidorChat.rolesUsuarios.containsKey(target)) {
                            ServidorChat.rolesUsuarios.put(target, "ORDINARIO"); // Regresión de rol
                            enviarMensajesASala("> Sistema: Los privilegios de " + target + " han expirado.");
                        }
                    }
                }, tiempoSecs * 1000L); // Convertido a milisegundos
            }
        } else {
            salida.println("> Sistema: No tienes permisos de Moderador para usar este comando.");
        }
    }

    /**
     * Hace Broadcast: itera sobre el array de sockets de la sala y manda un mensaje a todos
     */
    private void enviarMensajesASala(String txt) {
        Socket[] tabla = infoSala.getTabla();
        for (Socket s : tabla) {
            if (s != null && !s.isClosed()) {
                try { new PrintWriter(s.getOutputStream(), true).println(txt); } catch (IOException _) {}
            }
        }
    }

    /**
     * Limpia la memoria y las listas del servidor al detectar una desconexión
     */
    private void finalizarConexion() {
        if (infoSala != null && nombreCliente != null) {
            enviarMensajesASala("> " + nombreCliente + " ha abandonado el canal");
            enviarMensajesASala("###PARSER-SALE###" + nombreCliente);
            // Sincroniza la resta para evitar condiciones de carrera (Race Condition)
            synchronized (infoSala) { infoSala.setActuales(infoSala.getActuales() - 1); }
            ServidorChat.registrarLog(nombreCliente + " desconectado.");
        }
        // Lo sacamos de los HashMaps globales
        ServidorChat.nombresUsuarios.remove(socket);
        ServidorChat.rolesUsuarios.remove(nombreCliente);
        try { socket.close(); } catch (IOException _) {}
    }
}