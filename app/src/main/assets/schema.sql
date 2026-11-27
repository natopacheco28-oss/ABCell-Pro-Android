CREATE TABLE IF NOT EXISTS ordenes (
                folio INTEGER PRIMARY KEY AUTOINCREMENT,
                cliente_nombre TEXT NOT NULL,
                cliente_tel TEXT NOT NULL,
                categoria TEXT DEFAULT 'Celulares',
                marca TEXT NOT NULL,
                modelo TEXT NOT NULL,
                color TEXT DEFAULT '',
                imei TEXT DEFAULT '',
                pin_pass TEXT DEFAULT '',
                patron TEXT DEFAULT 'Sin patrón',
                estado_fisico TEXT DEFAULT '',
                falla TEXT NOT NULL,
                servicio_nombre TEXT DEFAULT '',
                ubicacion TEXT DEFAULT 'Sin asignar',
                estatus TEXT DEFAULT 'Recibido',
                costo_repuesto REAL DEFAULT 0.0,
                costo_total REAL DEFAULT 0.0,
                anticipo REAL DEFAULT 0.0,
                saldo REAL DEFAULT 0.0,
                metodo_pago TEXT DEFAULT 'Efectivo',
                notas_internas TEXT DEFAULT '',
                fecha_ingreso TEXT NOT NULL,
                fecha_actualizacion TEXT NOT NULL
            );
CREATE TABLE IF NOT EXISTS pagos_orden (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                folio INTEGER NOT NULL,
                monto REAL NOT NULL,
                metodo_pago TEXT DEFAULT 'Efectivo',
                fecha TEXT NOT NULL,
                FOREIGN KEY (folio) REFERENCES ordenes (folio)
            );
CREATE TABLE IF NOT EXISTS servicios_catalogo (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                categoria TEXT DEFAULT 'Celulares',
                marca TEXT DEFAULT '',
                modelo TEXT DEFAULT '',
                nombre TEXT NOT NULL,
                precio REAL DEFAULT 0.0,
                activo INTEGER DEFAULT 1
            );
CREATE TABLE IF NOT EXISTS ubicaciones (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL UNIQUE,
                tipo TEXT DEFAULT 'General',
                descripcion TEXT DEFAULT '',
                activo INTEGER DEFAULT 1
            );
CREATE TABLE IF NOT EXISTS notificaciones_wa (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                folio INTEGER DEFAULT 0,
                cliente_nombre TEXT DEFAULT '',
                telefono TEXT DEFAULT '',
                tipo TEXT DEFAULT '',
                mensaje TEXT DEFAULT '',
                estado TEXT DEFAULT 'Abierto en WA',
                fecha TEXT NOT NULL
            );
CREATE TABLE IF NOT EXISTS inventario (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                codigo TEXT DEFAULT '',
                nombre TEXT NOT NULL,
                categoria TEXT DEFAULT 'Refacciones',
                marca_modelo TEXT DEFAULT '',
                stock INTEGER DEFAULT 0,
                stock_minimo INTEGER DEFAULT 2,
                costo_compra REAL DEFAULT 0.0,
                precio_venta REAL DEFAULT 0.0,
                ubicacion TEXT DEFAULT '',
                notas TEXT DEFAULT '',
                fecha_actualizacion TEXT DEFAULT ''
            );
