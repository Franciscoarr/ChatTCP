package servidor;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorChat {

    //Mapa: Nombre de la sala -> Objeto InfoHilos de esa sala
    public static Map<String, InfoHilos> mapaSalas = new HashMap<>();
    //Usamos ConcurrentHashMap para evitar errores cuando varios entran a la vez
    public static Map<Socket, String> nombresUsuarios = new ConcurrentHashMap<>();
    static final int MAX_POR_SALA = 10;

    public static void main(String[] args) {
        int puerto = 5000;

        //Inicializamos las salas
        mapaSalas.put("#chathispano", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#irc-hispano", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#sevilla", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#amistad", new InfoHilos(MAX_POR_SALA));
        mapaSalas.put("#programacion", new InfoHilos(MAX_POR_SALA));

        System.out.println("SERVIDOR MULTI-SALA INICIADO EN PUERTO " + puerto);

        try (ServerSocket servidor = new ServerSocket(puerto)) {
            while (true) {
                //Aceptamos la conexión genérica
                Socket cliente = servidor.accept();

                //Lanzamos el hilo. El hilo se encargará de preguntar a que sala va
                //No pasamos 'infoh' aquí, porque no se sabe a qué sala quiere ir
                HiloServidorChat hilo = new HiloServidorChat(cliente);
                hilo.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}