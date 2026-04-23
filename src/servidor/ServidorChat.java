package servidor;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor principal que gestiona las salas y acepta conexiones
 */
public class ServidorChat {

    // Mapa global de salas para el extra multicanal
    public static Map<String, InfoHilos> mapaSalas = new HashMap<>();

    // Mapa de Sockets y sus Nombres para los mensajes privados y validación
    public static Map<Socket, String> nombresUsuarios = new ConcurrentHashMap<>();

    static final int MAX_POR_SALA = 10;

    public static void main(String[] args) {
        int puerto = 5000;

        // Inicialización de canales
        mapaSalas.put("#General", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#Anime", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#Videojuegos", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#Películas", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#Programacion", new InfoHilos(MAX_POR_SALA));

        System.out.println("[LOG] SERVIDOR MULTISALA INICIADO EN PUERTO " + puerto);

        try (ServerSocket servidor = new ServerSocket(puerto)) {
            while (true) {
                // El servidor se queda bloqueado en accept() esperando un cliente
                Socket cliente = servidor.accept();

                // Lanzamos un hilo nuevo para atender a ese cliente específico
                new HiloServidorChat(cliente).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}