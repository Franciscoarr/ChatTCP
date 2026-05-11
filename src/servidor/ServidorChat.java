package servidor;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clase de arranque principal del lado del Servidor
 * Su único trabajo es escuchar conexiones y derivarlas a Hilos independientes
 */
public class ServidorChat {

    // Mapa para el requisito extra de Múltiples Canales de Chat
    public static Map<String, InfoHilos> mapaSalas = new HashMap<>();

    // Almacenes globales con concurrencia segura (ConcurrentHashMap) para mensajes privados y roles
    public static Map<Socket, String> nombresUsuarios = new ConcurrentHashMap<>();
    public static Map<String, String> rolesUsuarios = new ConcurrentHashMap<>(); // Mapa para guardar Nick -> Rol (MODERADOR)

    // Almacena en la memoria viva todos los logs del sistema
    public static StringBuilder logHistorial = new StringBuilder();
    static final int MAX_POR_SALA = 10;

    public static void main(String[] args) {
        int puerto = 5000;

        // 1. Inicializar seguridad y crear archivo TXT si no existe
        GestorSeguridad.inicializarFicheroUsuarios();

        // 2. Inicializamos la estructura de las salas de chat
        mapaSalas.put("#General", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#Anime", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#Videojuegos", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#Programacion", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#Peliculas", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#Musica", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#Deportes", new InfoHilos(MAX_POR_SALA));

        registrarLog("SERVIDOR MULTISALA INICIADO EN PUERTO " + puerto);

        // 3. Hilo extra e independiente (Demonio) para el Backup de Logs por consola
        new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            while (true) {
                String input = sc.nextLine(); // Se queda esperando que se escriba en consola
                if (input.equalsIgnoreCase("BACKUP")) {
                    try (PrintWriter out = new PrintWriter(new FileWriter("backup_logs.txt", true))) {
                        out.println(logHistorial.toString()); // Vuelca toda la memoria a disco
                        System.out.println("[SISTEMA] Backup de logs guardado correctamente.");
                    } catch (IOException e) {
                        System.out.println("Error al guardar backup.");
                    }
                }
            }
        }).start();

        System.out.println("Escribe 'BACKUP' y pulsa Enter en la consola para guardar los logs.");

        // 4. Bucle infinito del ServerSocket
        try (ServerSocket servidor = new ServerSocket(puerto)) {
            while (true) {
                // El programa se bloquea/pausa en accept() hasta que un cliente intente conectarse
                Socket cliente = servidor.accept();
                // Delega la conexión aceptada a un trabajador (Hilo) independiente y continúa esperando
                new HiloServidorChat(cliente).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Función unificada para mostrar eventos por consola y a la vez guardarlos en el historial de backup
     */
    public static void registrarLog(String msg) {
        String linea = "[LOG] " + msg;
        System.out.println(linea); // Se imprime en la terminal del Servidor
        logHistorial.append(linea).append("\n"); // Se guarda en memoria para el posible BACKUP
    }
}