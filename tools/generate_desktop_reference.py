"""Regenerate the reference using the ORIGINAL desktop renderer, not an Android imitation.

Usage: .venv/bin/python tools/generate_desktop_reference.py /path/to/ABCell-Pro
Requires Pillow in that venv; the source checkout must be commit 199b9d6cba830a338f57e2ffb39f6490108722d8.
No real workshop/customer data is used. The reference intentionally has no workshop logo/access secrets.
"""
from pathlib import Path
import sys

if __name__ == '__main__':
    sys.path.insert(0, str(Path(sys.argv[1]).resolve()))
    from config.settings import ConfigManager
    from integrations.ticket_image import generar_imagen_ticket
    config = ConfigManager(config_path='')
    config.data['notas'].update(
        nombre_comercial='ABCell · Taller de celulares',
        direccion='Av. Central 120, Tuxtla Gutiérrez, Chiapas', telefono='961 123 4567',
        politicas_garantia='Garantía de 30 días sobre el servicio realizado. No cubre golpes, humedad ni daño por terceros.',
        texto_agradecimiento='¡Gracias por su preferencia!', mostrar_logo=False)
    order = dict(folio=1, fecha_ingreso='2026-11-27 12:30:00', estatus='Recibido',
                 cliente_nombre='Cliente de muestra', cliente_tel='961 000 0000',
                 marca='Samsung', modelo='Galaxy A52', color='Negro', imei='DEMO-123456',
                 ubicacion='Mostrador', estado_fisico='Equipo con funda; sin golpes visibles.',
                 falla='Cambio de pantalla y revisión del centro de carga.', costo_total=950, anticipo=300, saldo=650)
    destination = Path(__file__).resolve().parents[1] / 'docs/references/nota-pc.png'
    assert generar_imagen_ticket(order, config, str(destination))
    print(destination)
