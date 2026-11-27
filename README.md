# ABCell Móvil · Android

Adaptación **nativa Android** del administrador de talleres [ABCell Pro](https://github.com/natopacheco28-oss/ABCell-Pro). Nombre instalado: **ABCell Móvil**. Paquete: `com.abcell.movil`. Android **8.0 o posterior** (API 26). Primera versión: **0.1.0**.

El icono utiliza el emblema del logo original, sin la leyenda «Pro». **No existe la opción Utilidades Android, ni se empaqueta ADB.** El repositorio de escritorio no se modifica.

## Qué funciona en esta primera versión

- Inicio con órdenes en proceso, listas para entregar, saldos y alertas de inventario.
- Recepción de equipos: cliente, teléfono, categoría, marca, modelo, color, IMEI/serie, acceso opcional, accesorios, falla, servicio, ubicación, refacciones, total y anticipo.
- Búsqueda y filtro por estado; edición de órdenes; cambio de estado y ubicación; eliminación confirmada.
- Abonos con método de pago e historial. Validaciones para impedir importes negativos, pagos superiores al saldo y reducciones de total por debajo de lo ya abonado. Las operaciones de orden/anticipo y abono/saldo son transaccionales.
- Inventario con productos, existencias, stock mínimo, costo, precio y filtros de agotados/stock bajo.
- Alta, edición y eliminación de servicios y ubicaciones. Selección de servicio y precio al recibir un equipo.
- Reportes globales de cobros, cuentas pendientes, margen estimado de entregas y valor del inventario.
- Configuración del nombre, contacto, dirección y políticas del taller.
- Ticket de texto compartible, ticket imprimible/guardable como PDF mediante el sistema Android y apertura de mensaje de estado en WhatsApp. Nunca se comparten PIN, patrón ni notas internas en estos documentos.
- Exportación y restauración de respaldos JSON mediante el selector de documentos Android. Una restauración inválida revierte toda la transacción.
- Formularios desplazables y conservación del borrador del formulario principal al recrear la actividad, por ejemplo al girar el teléfono.

### Alcance y diferencias importantes

Esta es una **base funcional inicial**, no una conversión completa ni una réplica exacta de cada función de escritorio. Python/PySide6, los drivers Windows y ADB no se ejecutan dentro de la APK. Consulta [la procedencia y el alcance](docs/PORTABILIDAD.md).

- Los datos se guardan **solo en este dispositivo**, en SQLite privado. No hay servidor, cuentas, sincronización con PC ni importación directa de la base `.db` de escritorio. El respaldo JSON es propio de Móvil.
- Entregar una orden **no inventa un cobro**. El saldo se conserva; se pide confirmación si se entrega con deuda. Registra el pago real por separado.
- La impresión utiliza Android Print Framework. Necesita un servicio de impresión compatible para la impresora física; también permite «Guardar como PDF». No implementa conexión directa Bluetooth/USB ESC/POS de 58 mm.
- WhatsApp abre un mensaje para que el usuario confirme el envío; no lo envía en segundo plano ni certifica su entrega. Si se escriben diez dígitos se antepone `52` (México); para otros países, captura el prefijo internacional.
- Todavía no incluye logo personalizado del taller, ticket PNG, estadísticas por rango de fechas, importación de escritorio, protección con PIN/biometría ni bitácora de WhatsApp.
- No se solicitan permisos de red, contactos, teléfono ni acceso general al almacenamiento. La apertura de WhatsApp/navegador se delega al sistema y puede requerir internet en esa aplicación.
- La base local y los respaldos **no tienen cifrado propio de la app**. No registres contraseñas del equipo si no son necesarias; protege el teléfono con bloqueo y guarda los respaldos en un destino privado. La copia de seguridad automática Android está desactivada. Desinstalar borra los datos locales.

## Compilar automáticamente en GitHub

El workflow [Compilar ABCell Móvil APK](.github/workflows/android.yml) se ejecuta en cada `push`, pull request y manualmente. No necesitas instalar Android Studio para usarlo.

1. Sube estos cambios a este repositorio o intégralos a `main`. En una rama también se pueden compilar.
2. En GitHub abre **Actions → Compilar ABCell Móvil APK**.
3. Para lanzarlo a mano: **Run workflow**, selecciona la rama con este código y confirma.
4. Espera a que termine **Verificar y compilar APK**. Los otros dos trabajos ejecutan pruebas en emuladores API 29 y 35; revisa también sus resultados antes de distribuir.
5. Abre la ejecución y baja el artefacto **ABCell-Movil-APK** (ZIP). Contiene:
   - `ABCell-Movil.apk`: APK **debug instalable para pruebas**.
   - `SHA256SUMS.txt`: huella de integridad.
6. Extrae el ZIP en el teléfono, abre la APK y autoriza la instalación desde esa fuente cuando Android lo pida.

Los artefactos se conservan 30 días. Se requiere iniciar sesión en GitHub para descargarlos. El workflow no publica en Google Play ni crea una release automáticamente.

**Atención con las actualizaciones de prueba:** la firma debug puede cambiar entre ejecuciones de GitHub. Si Android rechaza instalar otra APK con firma distinta, **exporta primero un respaldo**; desinstalar elimina la base local. Para actualizaciones estables, usa siempre tu misma clave de distribución. No uses la APK debug en producción.

### APK de distribución con firma estable (opcional)

Crea y conserva fuera del repositorio tu keystore de distribución. Nunca lo subas a Git ni compartas contraseñas en el chat. En **Settings → Secrets and variables → Actions → New repository secret**, configura los cuatro secretos:

| Secreto | Valor |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Archivo `.jks` codificado en base64, sin saltos de línea |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del keystore |
| `ANDROID_KEY_ALIAS` | Alias de la clave |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave |

Si los cuatro existen, el mismo workflow compila y verifica una APK release firmada, publicada como **ABCell-Movil-Release** (`ABCell-Movil-release.apk`). Si no existe ninguno, solo genera debug. Si la configuración está incompleta, el paso de firma falla explícitamente. Los secretos no se usan en pull requests.

Respalda el keystore y sus contraseñas de forma segura: perderlos puede impedir actualizar instalaciones existentes. Incrementa `versionCode` y `versionName` en `app/build.gradle` en cada nueva versión distribuida. La configuración actual genera APK; publicar un AAB en Play Console queda fuera de esta primera entrega.

## Compilar localmente

Requisitos: **JDK 17**, Android SDK con plataforma **35** y build-tools **35.0.0**, y acceso a los repositorios Google/Maven/Gradle. Android Studio puede instalarlos. Configura `ANDROID_HOME` o `sdk.dir` en un `local.properties` que no se versiona.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
# Windows: gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Resultado: `app/build/outputs/apk/debug/app-debug.apk`.

Pruebas en emulador o teléfono conectado (API 26+):

```bash
./gradlew connectedDebugAndroidTest
```

Verificación del esquema y empaquetado, sin dependencias Python externas:

```bash
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

## Estructura

| Ruta | Responsabilidad |
|---|---|
| `app/src/main/java/com/abcell/movil/MainActivity.java` | Pantallas nativas, formularios, navegación e intents Android |
| `WorkshopDb.java` | SQLite, reglas de órdenes y pagos, configuración y respaldos |
| `Money.java` | Cálculo de importes en centavos |
| `TicketPrinter.java` | Impresión/PDF paginado con Android Print Framework |
| `app/src/main/assets/schema.sql` | Las seis tablas copiadas de ABCell Pro |
| `app/src/main/assets/catalogo.json` | Catálogos y datos iniciales del proyecto original |
| `app/src/main/res/` | Identidad, iconos, tema oscuro Lima y nombre de la app |
| `app/src/test/`, `app/src/androidTest/` | Pruebas monetarias, SQLite y navegación Android |
| `.github/workflows/android.yml` | Compilación, verificaciones, emuladores y artefactos APK |

Autoría original: **ABCell Profecional**. Véase [UPSTREAM.md](UPSTREAM.md) para la revisión de origen y atribuciones.
