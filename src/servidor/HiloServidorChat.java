package servidor;

import java.io.*;
import java.net.Socket;
import java.util.Map;

/**
 * Gestiona la comunicación con un cliente individual
 */
public class HiloServidorChat extends Thread {

    private final Socket socket;
    private InfoHilos infoSala;
    private BufferedReader entrada;
    private PrintWriter salida;
    private String nombreCliente;
    private String nombreSala;

    public HiloServidorChat(Socket socket) {
        this.socket = socket;
        try {
            // Creamos los flujos de comunicación
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // 1. Leemos credenciales iniciales
            nombreCliente = entrada.readLine();
            nombreSala = entrada.readLine();

            if (nombreCliente == null) return;

            // 2. Validación: ¿El Nick ya existe en el servidor?
            if (ServidorChat.nombresUsuarios.containsValue(nombreCliente)) {
                System.out.println("[LOG] Nick duplicado rechazado: " + nombreCliente);
                salida.println("###ERROR-NICK###");
                socket.close();
                return;
            }

            // 3. Asignación de sala
            this.infoSala = ServidorChat.mapaSalas.getOrDefault(nombreSala, ServidorChat.mapaSalas.get("#General"));

            // 4. Registro en el objeto compartido InfoHilos
            synchronized (infoSala) {
                if (infoSala.getActuales() < ServidorChat.MAX_POR_SALA) {
                    if (infoSala.addSocket(socket)) {
                        infoSala.setActuales(infoSala.getActuales() + 1);
                        ServidorChat.nombresUsuarios.put(socket, nombreCliente);
                        System.out.println("[LOG] " + nombreCliente + " conectado a " + nombreSala);
                    } else { socket.close(); return; }
                } else {
                    salida.println("Sala llena");
                    socket.close();
                    return;
                }
            }

            // --- PROTOCOLO DE ACTUALIZACIÓN DE LISTAS ---
            // A. Avisamos a los que ya estaban de que hemos llegado
            enviarMensajesASala("###PARSER-ENTRA###" + nombreCliente);
            enviarMensajesASala("> " + nombreCliente + " ha entrado en " + nombreSala);

            // B. Nos informamos de quiénes estaban ya para llenar nuestra lista derecha
            Socket[] tabla = infoSala.getTabla();
            for (Socket s : tabla) {
                if (s != null && !s.isClosed() && s != socket) {
                    String otro = ServidorChat.nombresUsuarios.get(s);
                    if (otro != null) salida.println("###PARSER-ENTRA###" + otro);
                }
            }

            // 5. Bucle principal: Escuchar mensajes del cliente
            String texto;
            while ((texto = entrada.readLine()) != null) {
                if (texto.equals("*****")) break; // Salida voluntaria

                // Mensaje Privado (1 a 1)
                if (texto.startsWith("/privado ")) {
                    procesarMensajePrivado(texto);
                } else {
                    // Mensaje General
                    String msj = nombreCliente + "> " + texto;
                    infoSala.setMensajes(infoSala.getMensajes() + msj + "\n");
                    enviarMensajesASala(msj);
                }
            }

        } catch (IOException e) {
            System.out.println("[LOG] Desconexión abrupta de " + nombreCliente);
        } finally {
            finalizarConexion();
        }
    }

    private void procesarMensajePrivado(String texto) {
        String[] partes = texto.split(" ", 3);
        if (partes.length == 3) {
            String destino = partes[1];
            String msj = partes[2];
            boolean enviado = false;

            for (Map.Entry<Socket, String> entry : ServidorChat.nombresUsuarios.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(destino)) {
                    try {
                        PrintWriter out = new PrintWriter(entry.getKey().getOutputStream(), true);
                        out.println("[PRIVADO de " + nombreCliente + "]: " + msj);
                        salida.println("[PRIVADO para " + destino + "]: " + msj);
                        enviado = true;
                        break;
                    } catch (IOException _) {}
                }
            }
            if (!enviado) salida.println("> Sistema: El usuario '" + destino + "' no está online");
        }
    }

    /**
     * Envía un mensaje a todos los sockets activos de la sala actual
     */
    private void enviarMensajesASala(String txt) {
        Socket[] tabla = infoSala.getTabla();
        for (Socket s : tabla) {
            if (s != null && !s.isClosed()) {
                try {
                    new PrintWriter(s.getOutputStream(), true).println(txt);
                } catch (IOException _) {}
            }
        }
    }

    private void finalizarConexion() {
        if (infoSala != null && nombreCliente != null) {
            enviarMensajesASala("> " + nombreCliente + " ha abandonado el canal");
            enviarMensajesASala("###PARSER-SALE###" + nombreCliente);
            synchronized (infoSala) {
                infoSala.setActuales(infoSala.getActuales() - 1);
            }
        }
        ServidorChat.nombresUsuarios.remove(socket);
        try { socket.close(); } catch (IOException _) {}
    }
}