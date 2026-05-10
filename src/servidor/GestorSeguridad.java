package servidor;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class GestorSeguridad {

    private static final String ARCHIVO_USUARIOS = "usuarios_chat.txt";
    private static final int MAX_INTENTOS_FALLIDOS = 3;
    // Mapa para protección DoS (IP -> Intentos fallidos)
    private static Map<String, Integer> intentosLoginPorIP = new HashMap<>();

    // Clave AES estática para cifrado de mensajes privados (16 bytes)
    private static final byte[] CLAVE_AES = "ClaveSecreta1234".getBytes();

    /**
     * Hashea una cadena usando SHA-256
     */
    public static String hashearSHA256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(texto.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
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
     * Crea el archivo de usuarios por defecto si no existe.
     */
    public static void inicializarFicheroUsuarios() {
        File file = new File(ARCHIVO_USUARIOS);
        if (!file.exists()) {
            try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
                // Formato: Nickname,HashPassword,Rol
                out.println("Admin," + hashearSHA256("admin123") + ",MODERADOR");
                out.println("Pepe," + hashearSHA256("pepe123") + ",ORDINARIO");
                System.out.println("[SEGURIDAD] Archivo de usuarios creado por defecto.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Registra un nuevo usuario en el archivo TXT si el Nick no está en uso
     */
    public static synchronized String registrarUsuario(String nick, String password) {
        // Verificar si ya existe
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

        // Si no existe, lo guardamos
        try (PrintWriter out = new PrintWriter(new FileWriter(ARCHIVO_USUARIOS, true))) {
            out.println(nick + "," + hashearSHA256(password) + ",ORDINARIO");
            return "OK";
        } catch (IOException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    /**
     * Valida el login comprobando el fichero y controlando fuerza bruta
     */
    public static String validarUsuario(String ip, String nick, String password) {
        // Protección DoS
        int intentos = intentosLoginPorIP.getOrDefault(ip, 0);
        if (intentos >= MAX_INTENTOS_FALLIDOS) {
            System.out.println("[ALERTA SEGURIDAD] IP Bloqueada por fuerza bruta: " + ip);
            return "BLOQUEADO";
        }

        String hashInput = hashearSHA256(password);

        try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_USUARIOS))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(",");
                if (partes.length == 3) {
                    if (partes[0].equalsIgnoreCase(nick) && partes[1].equals(hashInput)) {
                        intentosLoginPorIP.remove(ip); // Resetea intentos si acierta
                        return partes[2]; // Retorna el ROL
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Si falla, suma un intento a la IP
        intentosLoginPorIP.put(ip, intentos + 1);
        return null;
    }

    // --- MÉTODOS DE CIFRADO (Se mantienen igual) ---
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

    public static String descifrarAES(String mensajeCifrado) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(CLAVE_AES, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(mensajeCifrado)));
        } catch (Exception e) {
            return mensajeCifrado;
        }
    }
}