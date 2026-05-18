package servidor;

import java.net.Socket;

/**
 * Clase que actúa como modelo de datos compartido
 * Cada sala (Ej: #General, #Anime) tiene su propia instancia de InfoHilos
 */
public class InfoHilos {
    private int conexiones; // Total histórico de conexiones en esta sala
    private int actuales;   // Usuarios online AHORA MISMO
    private final int maximo; // Aforo de la sala
    private final Socket[] tabla; // Array donde se guardan las "mangueras" conectadas
    private String mensajes;  // Historial global de la pizarra
    private boolean suspendido; // Bandera de seguridad activada por el comando /suspend

    public InfoHilos(int maximo) {
        this.maximo = maximo;
        this.conexiones = 0;
        this.actuales = 0;
        this.tabla = new Socket[maximo];
        this.mensajes = "";
        this.suspendido = false;
    }

    // --- GETTERS Y SETTERS SINCRONIZADOS ---
    // Usamos 'synchronized' porque múltiples HilosServidorChat intentarán leer y modificar
    // estas variables al mismo tiempo, lo que podría causar bloqueos o incoherencias (Race Conditions)

    public synchronized boolean isSuspendido() { return suspendido; }
    public synchronized void setSuspendido(boolean suspendido) { this.suspendido = suspendido; }

    public synchronized int getConexiones() { return conexiones; }
    public synchronized int getActuales() { return actuales; }
    public synchronized void setActuales(int actuales) { this.actuales = actuales; }
    public synchronized String getMensajes() { return mensajes; }
    public synchronized void setMensajes(String mensajes) { this.mensajes = mensajes; }
    public synchronized Socket[] getTabla() { return tabla; }

    /**
     * Busca el primer hueco disponible (null) en la tabla para meter un nuevo socket
     * Esto evita excepciones ArrayIndexOutOfBounds
     */
    public synchronized boolean addSocket(Socket s) {
        for (int i = 0; i < maximo; i++) {
            if (tabla[i] == null || tabla[i].isClosed()) {
                tabla[i] = s;
                this.conexiones++;
                return true;
            }
        }
        return false; // Devuelve falso si la sala está llena (Array completo)
    }
}