# Origen de la adaptación

- Repositorio proporcionado por el propietario: https://github.com/natopacheco28-oss/ABCell-Pro
- Revisión consultada: `199b9d6cba830a338f57e2ffb39f6490108722d8` (`main`).
- Nombre móvil: **ABCell Móvil**.
- Autoría original conservada: **ABCell Profecional**.

## Código y recursos derivados

- `app/src/main/assets/schema.sql`: las seis sentencias `CREATE TABLE` de `DatabaseManager._crear_tablas` en `database/db_manager.py`, conservando nombres, campos y tipos.
- `app/src/main/assets/catalogo.json`: categorías, estados y métodos de `core/catalog_data.py`; servicios y ubicaciones de `_sembrar_datos_iniciales` en `database/db_manager.py`.
- `app/src/main/java/com/abcell/movil/WorkshopDb.java`: adaptación a SQLite Android de la persistencia y de las reglas de órdenes, abonos, inventario, catálogo y ubicaciones de `services/workshop_service.py`. Las operaciones monetarias se validan y se agrupan en transacciones.
- `app/src/main/res/drawable/brand_emblem.png` y los iconos de inicio: recorte y escalado del emblema de `ui/assets/abcell-pro.jpg` (sin el texto Pro).
- Colores del tema Lima Pro del proyecto original: fondo `#0c111b`, tarjetas `#151e2c`, texto `#edf2f7` y acento `#c5f467`.

El resto de la interfaz y las integraciones móviles se implementan usando el SDK Android; no se copia ni se ejecuta la interfaz PySide6. No se incorporan los binarios de escritorio, datos de clientes, contraseñas reales ni código ADB.

No se encontró un archivo LICENSE en la revisión original. Esta adaptación, solicitada por el propietario, no pretende asignar una licencia nueva al código o al logo original. Antes de redistribuir, el propietario debe decidir los permisos de uso aplicables.

## Herramientas de compilación

`gradlew`, `gradlew.bat` y `gradle/wrapper/gradle-wrapper.jar` se obtuvieron del repositorio oficial `gradle/gradle`, etiqueta `v8.11.1`. Gradle se distribuye bajo Apache License 2.0; los scripts conservan sus avisos originales. El runtime de la app usa el SDK Android; JUnit y AndroidX Test solo se utilizan en pruebas.
