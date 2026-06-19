# ChatTCP

Aplicación de chat cliente-servidor en **Java** construida sobre **Sockets TCP**, con interfaz gráfica en **Swing**, múltiples salas, sistema de roles (usuarios ordinarios y moderadores), cifrado de mensajes privados y persistencia de usuarios en disco.

## Características

- 💬 **Chat en tiempo real** mediante Sockets TCP, con un hilo independiente por cada cliente conectado (`HiloServidorChat`).
- 🗂️ **Múltiples canales/salas**: `#General`, `#Anime`, `#Videojuegos`, `#Programacion`, `#Peliculas`, `#Musica`, `#Deportes`, cada una con un aforo máximo configurable (10 usuarios por defecto).
- 🔐 **Login y registro de usuarios**, con contraseñas almacenadas como hash **SHA-256** en `usuarios_chat.txt` (nunca en texto plano).
- 🛡️ **Protección contra fuerza bruta**: bloqueo de IP tras 3 intentos fallidos de login.
- 🔒 **Mensajes privados y archivos privados cifrados con AES**, para que no puedan leerse interceptando el tráfico.
- 📎 **Compartición de archivos**, tanto en privado como en la sala completa (`/file`, `/fileall`).
- 👮 **Roles de usuario**: `ORDINARIO` y `MODERADOR`, con comandos exclusivos de moderación.
- 🗑️ **Borrado de mensajes**: cada usuario puede borrar su último mensaje; un moderador puede borrar el de cualquiera.
- 📝 **Sistema de logs en memoria** con backup manual a disco (`backup_logs.txt`) escribiendo `BACKUP` en la consola del servidor.
- 🖥️ **Interfaz gráfica** construida con Swing: lista de salas, área de chat, lista de usuarios conectados y envío de archivos.

## Arquitectura

```
┌────────────────────┐        Socket TCP (puerto 5000)        ┌────────────────────────┐
│  ClienteChatSwing   │ ───────────────────────────────────── │     ServidorChat        │
│  (GUI / cliente)    │                                        │  (ServerSocket, accept) │
└────────────────────┘                                        └───────────┬─────────────┘
                                                                            │ por cada cliente
                                                                            ▼
                                                                ┌────────────────────────┐
                                                                │   HiloServidorChat      │
                                                                │ (login, comandos, chat) │
                                                                └───────────┬─────────────┘
                                                                            │
                                                    ┌───────────────────────┼───────────────────────┐
                                                    ▼                       ▼                        ▼
                                            GestorSeguridad           InfoHilos                Mapas globales
                                         (hash, AES, anti fuerza   (estado por sala:       (usuarios y roles
                                              bruta)                sockets, mensajes,        conectados)
                                                                     suspendido)
```

- **`ServidorChat`**: punto de entrada del servidor. Abre el `ServerSocket` en el puerto `5000`, inicializa las salas y delega cada conexión entrante a un nuevo `HiloServidorChat`.
- **`HiloServidorChat`**: gestiona el ciclo de vida completo de un cliente (login/registro, recepción de mensajes, interpretación de comandos y difusión a la sala).
- **`InfoHilos`**: modelo de datos de cada sala (usuarios conectados, historial de mensajes, aforo, estado de suspensión), con acceso sincronizado para evitar condiciones de carrera.
- **`GestorSeguridad`**: hashing SHA-256 de contraseñas, cifrado/descifrado AES de mensajes privados, alta de usuarios y control de intentos de login por IP.
- **`ClienteChatSwing`**: interfaz gráfica de escritorio que se conecta al servidor, gestiona el formulario de login/registro, muestra los canales, el chat y los usuarios, y permite enviar mensajes, privados y archivos.

## Protocolo de comunicación

La comunicación usa texto plano por línea sobre el socket, con un separador `###`:

| Mensaje | Sentido | Descripción |
|---|---|---|
| `LOGIN###nick###password` / `REGISTER###nick###password` | Cliente → Servidor | Autenticación inicial (segunda línea enviada es el nombre de la sala). |
| `###LOGIN-OK###ROL` | Servidor → Cliente | Acceso concedido, indica el rol asignado. |
| `###ERROR-LOGIN###mensaje` | Servidor → Cliente | Error de credenciales, usuario duplicado, sala llena o IP bloqueada. |
| `###PARSER-ENTRA###nick` / `###PARSER-SALE###nick` | Servidor → Cliente | Notifica entrada/salida de un usuario para actualizar la lista. |
| `###DEL-LAST###nick` | Servidor → Cliente | Indica que se debe eliminar de la vista el último mensaje de `nick`. |
| `###KICKED###` | Servidor → Cliente | El usuario ha sido expulsado por un moderador. |
| `*****` | Cliente → Servidor | Desconexión voluntaria (salir o cambiar de sala). |

## Comandos disponibles en el chat

**Para todos los usuarios:**

| Comando | Descripción |
|---|---|
| `/help` | Muestra la ayuda con los comandos disponibles. |
| `/privado [nick] [mensaje]` | Envía un mensaje privado cifrado con AES. |
| `/file [nick]` | Envía un archivo privado cifrado (desde el botón "Archivo Priv."). |
| `/fileall` | Comparte un archivo en la sala (desde el botón "Archivo Púb."). |
| `/delmsg` | Borra tu último mensaje enviado. |
| `/clear` | Limpia el área de chat local (no afecta al historial del servidor). |

**Solo moderadores:**

| Comando | Descripción |
|---|---|
| `/kick [nick]` | Expulsa a un usuario de la sala. |
| `/suspend` | Suspende/reanuda el canal (solo moderadores pueden escribir mientras está suspendido). |
| `/promote [nick] [segundos]` | Asciende temporalmente a un usuario a moderador durante el tiempo indicado. |
| `/delmsg [nick]` | Borra el último mensaje de otro usuario. |

## Requisitos

- **JDK 8 o superior** (usa `javax.crypto`, `javax.swing` y `java.util.concurrent`, todos incluidos en el JDK estándar).
- No requiere librerías externas ni gestor de dependencias (Maven/Gradle): es un proyecto Java plano (estructurado originalmente como proyecto de IntelliJ IDEA).

## Cómo ejecutarlo

### 1. Clonar el repositorio

```bash
git clone https://github.com/Franciscoarr/ChatTCP.git
cd ChatTCP
```

### 2. Compilar

```bash
javac -d out -encoding UTF-8 src/servidor/*.java src/cliente/*.java
```

### 3. Levantar el servidor

```bash
java -cp out servidor.ServidorChat
```

El servidor escuchará en el puerto `5000` y creará automáticamente `usuarios_chat.txt` con dos usuarios de ejemplo si el archivo no existe:

| Nick | Password | Rol |
|---|---|---|
| `Admin` | `admin123` | MODERADOR |
| `Pepe` | `pepe123` | ORDINARIO |

> 💡 En cualquier momento puedes escribir `BACKUP` y pulsar Enter en la consola del servidor para volcar el historial de logs a `backup_logs.txt`.

### 4. Lanzar uno o varios clientes

En otra terminal (puedes abrir varias instancias para simular varios usuarios):

```bash
java -cp out cliente.ClienteChatSwing
```

Se abrirá un formulario para iniciar sesión o registrarte. Tras autenticarte correctamente, accederás a la sala `#General` y podrás moverte entre canales desde el panel izquierdo.

> ⚠️ El cliente se conecta por defecto a `localhost`. Si quieres probarlo entre varios equipos en red, deberás modificar la dirección en `ClienteChatSwing.conectarSala()` (`new Socket("localhost", 5000)`) por la IP del servidor.

## Estructura del proyecto

```
ChatTCP/
├── src/
│   ├── cliente/
│   │   └── ClienteChatSwing.java   # GUI del cliente (Swing)
│   └── servidor/
│       ├── ServidorChat.java       # Punto de entrada del servidor
│       ├── HiloServidorChat.java   # Hilo por cliente: login, comandos, chat
│       ├── InfoHilos.java          # Estado de cada sala (sockets, mensajes, aforo)
│       └── GestorSeguridad.java    # Hash SHA-256, cifrado AES, anti fuerza bruta
├── usuarios_chat.txt               # Persistencia de usuarios (nick, hash, rol)
├── backup_logs.txt                 # Backup de logs generado bajo demanda
└── ChatTCP.iml                     # Configuración de módulo de IntelliJ IDEA
```

## Notas de seguridad

Este proyecto tiene fines educativos (práctica de redes/sockets en Java) y simplifica algunos aspectos de seguridad que **no deberían usarse tal cual en producción**:

- La clave AES está fijada en el código (`GestorSeguridad.CLAVE_AES`), en lugar de gestionarse mediante un intercambio de claves seguro.
- Las credenciales se almacenan en un fichero de texto plano (`usuarios_chat.txt`), aunque la contraseña sí va hasheada con SHA-256.
- No se usa TLS/SSL sobre el socket: solo el contenido de mensajes privados y archivos privados va cifrado con AES a nivel de aplicación.

## Tecnologías

- Java SE (Sockets, `java.util.concurrent`, `javax.crypto`)
- Swing (interfaz gráfica del cliente)
- SHA-256 / AES (`java.security`, `javax.crypto`)
