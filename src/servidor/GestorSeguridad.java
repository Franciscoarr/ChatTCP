package servidor;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Clase encargada de manejar todos los algoritmos criptográficos y accesos a disco
 */
public class GestorSeguridad {

    private static final String ARCHIVO_USUARIOS = "usuarios_chat.txt";
    private static final int MAX_INTENTOS_FALLIDOS = 3; // Límite de intentos para fuerza bruta

    // Mapa para protección DoS (Asocia una Dirección IP -> Número de Intentos fallidos)
    private static Map<String, Integer> intentosLoginPorIP = new HashMap<>();

    // Clave AES estática para cifrado de mensajes privados (16 bytes = 128 bits)
    private static final byte[] CLAVE_AES = "ClaveSecreta1234".getBytes();

    /**
     * Aplica el algoritmo de Hashing SHA-256 a un texto (Irreversible)
     * Utilizado para guardar y comparar las contraseñas sin que se vean en texto plano
     */
    public static String hashearSHA256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(texto.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            // Conversión de bytes a formato hexadecimal legible
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Comprueba si el fichero de usuarios existe. Si no, crea uno por defecto con un Admin
     */
    public static void inicializarFicheroUsuarios() {
        File file = new File(ARCHIVO_USUARIOS);
        if (!file.exists()) {
            try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
                // Formato CSV (valores separados por comas): Nickname, HashPassword, Rol
                out.println("Admin," + hashearSHA256("admin123") + ",MODERADOR");
                out.println("Pepe," + hashearSHA256("pepe123") + ",ORDINARIO");
                System.out.println("[SEGURIDAD] Archivo de usuarios creado por defecto.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Lee el archivo para verificar que el nick está libre y lo añade
     * Sincronizado para evitar que dos usuarios se registren con el mismo nick a la vez
     */
    public static synchronized String registrarUsuario(String nick, String password) {
        // 1. Verificamos si el usuario ya existe en el TXT
        try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_USUARIOS))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(",");
                if (partes.length >= 1 && partes[0].equalsIgnoreCase(nick)) {
                    return "EXISTS";
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2. Si no existe, lo escribimos al final del archivo con rol ORDINARIO
        try (PrintWriter out = new PrintWriter(new FileWriter(ARCHIVO_USUARIOS, true))) {
            out.println(nick + "," + hashearSHA256(password) + ",ORDINARIO");
            return "OK";
        } catch (IOException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    /**
     * Valida si el usuario y contraseña coinciden con el archivo de texto
     * Gestiona también la protección DoS (Fuerza Bruta) bloqueando IPs
     */
    public static String validarUsuario(String ip, String nick, String password) {
        int intentos = intentosLoginPorIP.getOrDefault(ip, 0);

        // Bloqueo de seguridad si la IP superó el límite de fallos
        if (intentos >= MAX_INTENTOS_FALLIDOS) {
            System.out.println("[ALERTA SEGURIDAD] IP Bloqueada por fuerza bruta: " + ip);
            return "BLOQUEADO";
        }

        // Calculamos el hash de lo que ha escrito el usuario para compararlo
        String hashInput = hashearSHA256(password);

        try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_USUARIOS))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(",");
                if (partes.length == 3) {
                    // Si el nick y el Hash de la contraseña coinciden
                    if (partes[0].equalsIgnoreCase(nick) && partes[1].equals(hashInput)) {
                        intentosLoginPorIP.remove(ip); // Éxito: Limpiamos su historial de fallos
                        return partes[2]; // Retornamos el Rol (Ej: MODERADOR o ORDINARIO)
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Si llega aquí, significa que la contraseña estaba mal. Le restamos un intento a su IP
        intentosLoginPorIP.put(ip, intentos + 1);
        return null;
    }

    /**
     * Calcula cuántos intentos le quedan a una IP antes de ser bloqueada
     */
    public static int getIntentosRestantes(String ip) {
        int intentos = intentosLoginPorIP.getOrDefault(ip, 0);
        return MAX_INTENTOS_FALLIDOS - intentos;
    }

    /**
     * Cifrado Simétrico bidireccional (AES).
     * Se usa para cifrar los mensajes y que nadie en la red pueda interceptarlos
     */
    public static String cifrarAES(String mensaje) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(CLAVE_AES, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(mensaje.getBytes("UTF-8")));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Desencripta un string cifrado en AES usando la clave secreta
     */
    public static String descifrarAES(String mensajeCifrado) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(CLAVE_AES, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(mensajeCifrado)));
        } catch (Exception e) {
            return mensajeCifrado; // Failsafe: Devuelve lo original si no era AES
        }
    }
}