import sys
sys.path.append(r'C:\Users\F.Fumi\AppData\Roaming\Python\Python313\site-packages')
from PIL import Image
import os

src = r'C:\Users\F.Fumi\OBDReader\graphics\obdreader_icon_512.png'
img = Image.open(src).convert('RGBA')
print('Sorgente aperto:', img.size)

sizes = {
    'mipmap-mdpi':    48,
    'mipmap-hdpi':    72,
    'mipmap-xhdpi':   96,
    'mipmap-xxhdpi':  144,
    'mipmap-xxxhdpi': 192,
}

base = r'C:\Users\F.Fumi\OBDReader\app\src\main\res'

# Genera foreground con sfondo bianco rimosso (trasparente)
# per il sistema adaptive icon
def make_transparent(image, threshold=240):
    """Rende trasparenti i pixel bianchi/quasi-bianchi (sfondo)."""
    data = image.getdata()
    new_data = []
    for r, g, b, a in data:
        if r >= threshold and g >= threshold and b >= threshold:
            new_data.append((r, g, b, 0))
        else:
            new_data.append((r, g, b, a))
    image.putdata(new_data)
    return image

fg_img = make_transparent(img.copy())

for folder, size in sizes.items():
    out_dir = os.path.join(base, folder)
    # ic_launcher e ic_launcher_round: icona completa (RGB, sfondo bianco)
    full = img.resize((size, size), Image.LANCZOS)
    for name in ['ic_launcher.webp', 'ic_launcher_round.webp']:
        out_path = os.path.join(out_dir, name)
        full.save(out_path, 'WEBP', quality=95)
        print(f'OK: {out_path}')
    # ic_launcher_foreground: sfondo trasparente per adaptive icon
    fg = fg_img.resize((size, size), Image.LANCZOS)
    out_path = os.path.join(out_dir, 'ic_launcher_foreground.webp')
    fg.save(out_path, 'WEBP', quality=95)
    print(f'OK: {out_path}')

print('Fatto.')



