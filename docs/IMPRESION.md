# Notas gráficas e impresión Bluetooth · 0.2.0

## Diseño tomado de la nota digital de PC

La referencia es `integrations/ticket_image.py` de ABCell Pro, revisión `199b9d6cba830a338f57e2ffb39f6490108722d8`.

`ReceiptRenderer` conserva su composición: fondo blanco, logo del taller centrado, nombre comercial en mayúsculas, título NOTA DE TALLER, separadores, folio azul grande, fecha y estado centrados, etiquetas de cliente/equipo, recuadro de total/anticipo/saldo, contacto, garantía, agradecimiento y pie con folio/negocio. Los colores digitales son los originales: azul `#0284C7`, texto `#0F172A`, gris `#64748B`, líneas `#E2E8F0` y recuadro `#E0F2FE`.

**No hay una plantilla de texto crudo para la impresión.** PNG, vista previa, PDF e imagen ESC/POS salen del mismo compositor gráfico. El texto se mide y envuelve antes de crear la imagen; no se recorta la nota a una altura fija. Los importes largos pasan a otra línea cuando hace falta. Si una nota supera 16,000 píxeles de alto, se informa del límite antes de enviarla, sin imprimirla truncada.

El PNG digital tiene 640 px como el original. En térmica se conservan secciones, jerarquía, bordes y logo, pero se reacomoda el contenido para el ancho útil y se elimina el fondo azul para ahorrar papel y mejorar contraste. **Una térmica monocroma no reproduce el azul.** La tipografía Android y el reflujo a 58/80 mm impiden prometer igualdad píxel por píxel con una imagen generada en Windows/Arial; se replica el diseño, no una fuente propietaria de Windows.

- Referencia real generada por el código de PC con datos ficticios: [nota-pc.png](references/nota-pc.png).
- Regeneración: `tools/generate_desktop_reference.py /ruta/ABCell-Pro`, usando Pillow y la revisión indicada. En Linux el renderer original usa su fuente disponible (DejaVu).
- Las pruebas Android generan PNG reales de la versión móvil en **notas-preview-android-29** y **notas-preview-android-35**, disponibles como artefactos de Actions. Incluyen nota digital con/sin logo, los tres perfiles térmicos y una descripción larga. `referencia-nota-PC.png` permite comparar la composición con la salida del código original, usando los mismos datos ficticios.

## Uso

1. En **Más → Configuración y respaldos**, guarda nombre, teléfono, dirección, garantía y agradecimiento.
2. Usa **Elegir logo PNG / JPG / WebP** (hasta 4 MB). Se guarda como PNG pequeño dentro de la configuración y del respaldo JSON. Quitar logo no afecta el icono de la app. No se sustituye un logo ausente por el logo de ABCell.
3. En una orden, entra en **Ver / compartir nota digital**. Verás la nota completa y podrás compartir un **PNG**, no un mensaje plano. Para PDF utiliza **Imprimir / guardar nota PDF** y el destino Guardar como PDF de Android.
4. En **Más → Impresora Bluetooth · 58 / 80 mm** o desde la orden, pulsa **Buscar impresoras Bluetooth**. Concede los permisos pedidos y enciende Bluetooth.
5. Selecciona tu dispositivo. Si no está vinculado, Android gestiona el emparejamiento; introduce el PIN indicado por el fabricante, no el PIN de acceso de un teléfono del cliente.
6. Selecciona el perfil, imprime la nota de prueba y verifica los bordes y la legibilidad.
7. Desde la orden, confirma la impresora y envía una copia. La app no imprime automáticamente al detectar, seleccionar o vincular un dispositivo.

## Compatibilidad real

| Perfil | Papel físico | Ancho raster | Ancho útil aprox. a 203 dpi |
|---|---|---|---|
| 58 mm | 58 mm | 384 puntos | 48 mm |
| 80 mm | 80 mm | 576 puntos | 72 mm |
| 80 mm alternativo | 80 mm | 512 puntos | 64 mm |

La conexión directa usa **Bluetooth clásico RFCOMM/SPP** (UUID estándar `00001101-0000-1000-8000-00805F9B34FB`) y gráficos **ESC/POS GS v 0**. No necesita una página de códigos de texto: acentos, logo, líneas y montos se transmiten como imagen. Se envían bandas de 64 filas con pausas para no saturar tan fácilmente impresoras de buffer pequeño. Al final se avanza el papel; el corte es opcional y viene desactivado.

**No se garantiza compatibilidad con toda impresora que diga Bluetooth o 58/80 mm.** No incluye BLE-only/GATT, Wi-Fi, USB, TSPL, CPCL, ZPL, protocolos propietarios de algunas impresoras de etiquetas ni comandos raster alternativos de determinados clones. La detección muestra dispositivos clásicos/duales; no puede demostrar que un dispositivo sea una impresora ni conocer su ancho real. Si el manual no indica SPP + ESC/POS raster, consulta el modelo antes de comprarla.

Hay una opción **Conexión SPP alternativa** para algunos equipos que no negocian RFCOMM seguro. Solo debe activarse si falla la conexión normal; implica transporte Bluetooth sin la misma autenticación. Se sigue exigiendo seleccionar un dispositivo vinculado. No hay reintentos ciegos ni fallback por reflexión a canales privados.

Para impresoras fuera de ese protocolo puedes compartir el PNG con su aplicación oficial o usar un servicio de impresión Android compatible. El servicio puede sustituir el tamaño de rollo propuesto por otro; comprueba su vista previa. PDF/impresión Android utiliza el mismo bitmap y pagina si el servicio obliga a un formato más corto.

## Permisos y privacidad

- Android 12+: **Dispositivos cercanos**, `BLUETOOTH_SCAN` y `BLUETOOTH_CONNECT`; la búsqueda declara `neverForLocation`.
- Android 8–11: permisos Bluetooth heredados; **ubicación precisa solo para descubrir dispositivos**, porque Android lo exige. También puede exigir el interruptor de Ubicación activo. La app no consulta ni almacena coordenadas. Imprimir con una impresora ya vinculada no pide ubicación.
- No se agregan permisos de internet, contactos ni almacenamiento general. El logo entra por el selector de documentos; el PNG se comparte mediante FileProvider no exportado con acceso temporal a `cache/receipts/` únicamente.
- PIN y patrón están **ocultos por defecto**. Si necesitas replicar también esos campos del PC, actívalos explícitamente en Configuración; una advertencia aparece en la vista previa. Las notas internas nunca salen. Una vez compartido/impreso un documento sensible no es posible revocarlo.
- La dirección Bluetooth elegida, el perfil y el logo viajan en el respaldo JSON, que sigue sin cifrar.

## Errores, cancelación y validación pendiente

La conexión tiene límite de 20 segundos y el envío de 120 segundos. Cancelar cierra el socket; salir/destruir la pantalla de impresora cancela la operación y no la reenvía. **Puede haberse impreso parte de la nota antes de un error o cancelación**: comprueba el papel antes de reintentar para evitar duplicados. «Datos enviados» solo confirma escritura del transporte, no que exista papel, que la tapa esté cerrada o que la impresión física haya terminado.

Las pruebas automatizadas comprueban la composición y sus límites, privacidad, logo respaldable, perfiles de papel, bytes raster, orden de bits, transparencia, bandas, errores de escritura y corte opcional. Los emuladores no certifican radio Bluetooth, calidad física ni un modelo de impresora concreto. Antes de uso real hay que probar detección, permisos rechazados, vinculación, desconexión a mitad del trabajo, cancelación, márgenes y corte con la impresora física del taller.

La versión sube a `versionCode=2`, sin cambiar el esquema de datos. Si la APK debug nueva tiene una firma distinta, **exporta un respaldo antes de desinstalar la anterior**. Para actualizar conservando datos sin reinstalar se necesita la misma clave de firma.
