# Portabilidad y verificación

## Decisiones de la versión inicial

ABCell Pro usa Python/PySide6 y está orientado a escritorio. Se aprovechan su esquema SQLite, catálogos y reglas, adaptando la interfaz a controles nativos Android en Java 17. Se evita introducir un servidor externo, un WebView, PySide6 y las herramientas ADB dentro de una APK.

Las seis tablas originales se mantienen y se agrega `mobile_settings` para respaldar los ajustes junto con los datos. Esto conserva conceptos y campos del origen, **no implica compatibilidad automática de archivos**: las migraciones históricas del escritorio no están implementadas en Móvil. No sustituyas manualmente la base privada por una base de PC.

Las diferencias de negocio intencionadas son:

1. Orden y anticipo se escriben en una sola transacción; abono y saldo también.
2. Los importes introducidos se calculan como centavos enteros. La persistencia `REAL` se mantiene por coherencia con las tablas originales; se convierte a centavos al operar.
3. No se permiten abonos de cero, negativos o mayores al saldo, ni totales por debajo de lo ya abonado. No hay módulo de reembolsos.
4. Pasar a Entregado no liquida automáticamente una deuda. Se confirma la entrega con saldo pendiente.
5. Renombrar una ubicación actualiza las referencias de órdenes e inventario; no se permite borrar una ubicación todavía utilizada.
6. Los respaldos son JSON versión 1, con límites de tamaño y validación en transacción. Solo aceptan el formato propio de Móvil. No se cifran.
7. Borrar una orden elimina sus abonos y afecta los reportes históricos. La interfaz pide confirmación; para trazabilidad contable es preferible conservarla como Cancelado.

## Validación automatizada

- Python: existencia de las seis tablas, carga de semillas originales, nombre/iconos y ausencia de permisos y payload de escritorio/ADB.
- JUnit local: importes exactos, separador decimal, límites, saldo, rechazos de cantidades inválidas y sobrepagos.
- Instrumentación Android: semillas, creación/anticipo, abono, persistencia tras reapertura, pagos inválidos sin escrituras, límites de edición, entrega sin cobro ficticio, respaldo/restauración, reversión ante respaldo inválido, borrado con hijos y propagación del nombre de ubicación.
- Navegación Android: apertura, recepción, conservación del borrador al recrear actividad y menú de herramientas sin Utilidades Android.
- Lint Android y ensamblado de APK.

GitHub Actions ejecuta las pruebas de dispositivo en API 29 y 35. Definirlas no equivale a que ya hayan pasado: consulta el estado de cada ejecución y sus reportes en Actions. No hay pruebas automáticas de envío real de WhatsApp, impresora física, permisos de proveedores de documentos o todos los tamaños/tipos de teléfono.

## Prueba manual recomendada antes de usar datos reales

- [ ] Instalar en un teléfono Android real y comprobar nombre e icono.
- [ ] Crear una orden con anticipo, cerrar/reabrir y comprobar folio, abono y saldo.
- [ ] Registrar un abono parcial y liquidar; intentar sobrepago y comprobar rechazo.
- [ ] Editar, cambiar ubicación y todos los estados, incluida entrega con saldo.
- [ ] Probar teléfono pequeño, orientación horizontal, teclado y tamaño de fuente aumentado.
- [ ] Crear/editar productos, agotar existencias y comprobar los filtros de inventario.
- [ ] Crear/editar servicios y ubicaciones; intentar eliminar una ubicación utilizada.
- [ ] Guardar datos del taller; compartir ticket y guardar PDF; verificar que no salen PIN, patrón ni notas internas.
- [ ] Probar impresión con el servicio Android de la impresora que se utilizará.
- [ ] Abrir WhatsApp y verificar cliente, estado y saldo antes de enviar.
- [ ] Exportar un respaldo, copiarlo fuera del teléfono, restaurarlo en una instalación de pruebas y comparar datos y ajustes.
- [ ] Intentar restaurar un JSON inválido y comprobar que los datos anteriores se conservan.
- [ ] Configurar firma release estable y probar una actualización sin pérdida de datos.
