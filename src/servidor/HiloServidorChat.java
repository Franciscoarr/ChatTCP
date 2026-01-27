package servidor;

import java.io.*;
import java.net.Socket;

public class HiloServidorChat extends Thread {

    private final Socket socket;
    private InfoHilos infoSala; //La sala donde estará el usuario
    private BufferedReader entrada;
    private PrintWriter salida;
    private String nombreCliente;
    private String nombreSala;

    public HiloServidorChat(Socket socket) {
        this.socket = socket;
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            //Leer datos iniciales (Protocolo: Nombre -> Sala)
            nombreCliente = entrada.readLine();
            nombreSala = entrada.readLine();

            //Verificación de seguridad por si envían null
            if (nombreCliente == null || nombreSala == null) {
                socket.close();
                return;
            }

            //Buscar la sala
            if (ServidorChat.mapaSalas.containsKey(nombreSala)) {
                this.infoSala = ServidorChat.mapaSalas.get(nombreSala);
            } else {
                //Si la sala no existe, lo metemos en una por defecto o le mostramos un error
                this.infoSala = ServidorChat.mapaSalas.get("#General");
                nombreSala = "#General";
            }

            //Registrar usuario en la sala
            synchronized (infoSala) {
                if (infoSala.getActuales() < 10) {
                    infoSala.addSocket(socket, infoSala.getConexiones());
                    infoSala.setConexiones(infoSala.getConexiones() + 1);
                    infoSala.setActuales(infoSala.getActuales() + 1);
                    //Guarda quien es este socket
                    ServidorChat.nombresUsuarios.put(socket, nombreCliente);
                } else {
                    salida.println("Sala llena");
                    socket.close();
                    return;
                }
            }

            //Avisar entrada
            String aviso = "> " + nombreCliente + " ha entrado en " + nombreSala;
            enviarMensajesASala(aviso);

            //Enviar historial previo de esa sala al nuevo usuario
            salida.println(infoSala.getMensajes());

            //Recorremos los sockets de la sala actual
            Socket[] socketsEnSala = infoSala.getTabla();
            for (Socket s : socketsEnSala) {
                if (s != null && !s.isClosed() && s != socket) {
                    //Recuperamos el nombre del mapa global
                    String nombreOtro = ServidorChat.nombresUsuarios.get(s);
                    if (nombreOtro != null) {
                        salida.println("###PARSER-ENTRA###" + nombreOtro);
                    }
                }
            }

            //Bucle de mensajes
            String texto;
            while ((texto = entrada.readLine()) != null) {
                if (texto.equals("*****")) break;

                String msjFinal = nombreCliente + "> " + texto;
                //Guardamos mensaje en el historial de esa sala
                infoSala.setMensajes(infoSala.getMensajes() + msjFinal + "\n");
                enviarMensajesASala(msjFinal);
            }

        } catch (IOException e) {
            System.out.println("Cliente desconectado abruptamente");
        } finally {
            //Salida
            if (infoSala != null) {
                enviarMensajesASala("> " + nombreCliente + " ha salido de " + nombreSala);
                enviarMensajesASala("###PARSER-SALE###" + nombreCliente);
                synchronized (infoSala) {
                    infoSala.setActuales(infoSala.getActuales() - 1);
                }
            }

            //Borrar el mapa para liberar memoria
            ServidorChat.nombresUsuarios.remove(socket);

            try {
                socket.close();
            } catch (IOException e)
            {}
        }
    }

    private void enviarMensajesASala(String txt) {
        //Solo recorremos el array de sockets de esta sala (infoSala)
        Socket[] tabla = infoSala.getTabla();
        //Usamos el contador histórico para iterar, verificando null y closed
        for (Socket s : tabla) {
            if (s != null && !s.isClosed()) {
                try {
                    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                    out.println(txt);
                } catch (IOException _) { }
            }
        }
    }
}