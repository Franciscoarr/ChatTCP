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

public class ServidorChat {

    public static Map<String, InfoHilos> mapaSalas = new HashMap<>();
    public static Map<Socket, String> nombresUsuarios = new ConcurrentHashMap<>();
    public static Map<String, String> rolesUsuarios = new ConcurrentHashMap<>(); // Nick -> Rol

    // StringBuilder para ir guardando los logs en memoria hasta hacer backup
    public static StringBuilder logHistorial = new StringBuilder();
    static final int MAX_POR_SALA = 10;

    public static void main(String[] args) {
        int puerto = 5000;

        // Inicializar seguridad y usuarios
        GestorSeguridad.inicializarFicheroUsuarios();

        mapaSalas.put("#General", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#Anime", new InfoHilos(MAX_POR_SALA));

        registrarLog("SERVIDOR MULTISALA INICIADO EN PUERTO " + puerto);

        // Hilo extra para el Backup de Logs por consola
        new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            while (true) {
                String input = sc.nextLine();
                if (input.equalsIgnoreCase("BACKUP")) {
                    try (PrintWriter out = new PrintWriter(new FileWriter("backup_logs.txt", true))) {
                        out.println(logHistorial.toString());
                        System.out.println("[SISTEMA] Backup de logs guardado correctamente.");
                    } catch (IOException e) {
                        System.out.println("Error al guardar backup.");
                    }
                }
            }
        }).start();

        System.out.println("Escribe 'BACKUP' y pulsa Enter en la consola para guardar los logs.");

        try (ServerSocket servidor = new ServerSocket(puerto)) {
            while (true) {
                Socket cliente = servidor.accept();
                new HiloServidorChat(cliente).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void registrarLog(String msg) {
        String linea = "[LOG] " + msg;
        System.out.println(linea);
        logHistorial.append(linea).append("\n");
    }
}