package servidor;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ServidorChat {

    // Mapa: Nombre de la sala -> Objeto InfoHilos de esa sala
    public static Map<String, InfoHilos> mapaSalas = new HashMap<>();
    static final int MAX_POR_SALA = 10;

    public static void main(String[] args) {
        int puerto = 5000;

        // 1. Inicializamos las salas disponibles (Crea una "Pizarra" InfoHilos para cada una)
        mapaSalas.put("#chathispano", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#irc-hispano", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#sevilla", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#amistad", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#programacion", new InfoHilos(MAX_POR_SALA));

        System.out.println("SERVIDOR MULTI-SALA INICIADO EN PUERTO " + puerto);

        try (ServerSocket servidor = new ServerSocket(puerto)) {
            while (true) {
                // Aceptamos la conexión genérica
                Socket cliente = servidor.accept();

                // Lanzamos el hilo. El hilo se encargará de preguntar A QUÉ SALA va.
                // Nota: Ya no pasamos 'infoh' aquí, porque no sabemos a qué sala quiere ir todavía.
                HiloServidorChat hilo = new HiloServidorChat(cliente);
                hilo.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}