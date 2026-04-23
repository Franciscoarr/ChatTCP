package servidor;

import java.net.Socket;

/**
 * Objeto común compartido por los hilos de una sala
 */
public class InfoHilos {
    private int conexiones;       // Histórico total
    private int actuales;         // Conectados ahora mismo
    private final int maximo;     // Límite de la sala
    private final Socket[] tabla; // Array de Sockets
    private String mensajes;      // Registro de mensajes

    public InfoHilos(int maximo) {
        this.maximo = maximo;
        this.conexiones = 0;
        this.actuales = 0;
        this.tabla = new Socket[maximo];
        this.mensajes = "";
    }

    // --- Getters y Setters Sincronizados para evitar errores de concurrencia ---
    public synchronized int getConexiones() {
        return conexiones;
    }

    public synchronized int getActuales() {
        return actuales;
    }

    public synchronized void setActuales(int actuales) {
        this.actuales = actuales;
    }

    public synchronized String getMensajes() {
        return mensajes;
    }

    public synchronized void setMensajes(String mensajes) {
        this.mensajes = mensajes; }
    public synchronized Socket[] getTabla() { return tabla;
    }

    /**
     * Añade un socket al array en la primera posición libre
     */
    public synchronized boolean addSocket(Socket s) {
        for (int i = 0; i < maximo; i++) {
            if (tabla[i] == null || tabla[i].isClosed()) {
                tabla[i] = s;
                this.conexiones++;
                return true;
            }
        }
        return false;
    }
}