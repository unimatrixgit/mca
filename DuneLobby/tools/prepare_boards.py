"""Generate the lobby's original typographic panels (Pillow; no external artwork downloads)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
def font(size):
    for name in ['C:/Windows/Fonts/arial.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf']:
        if Path(name).exists():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default(size=size)

def board(name, title, subtitle, lines):
    image = Image.new('RGB', (1280, 800), '#14131f')
    draw = ImageDraw.Draw(image)
    draw.rectangle((22, 22, 1257, 777), outline='#ba914e', width=5)
    draw.rectangle((40, 40, 1239, 759), outline='#473b44', width=2)
    draw.text((85, 80), title, font=font(72), fill='#e8bf70')
    draw.text((88, 180), subtitle, font=font(29), fill='#aea7c5')
    draw.line((85, 245, 1190, 245), fill='#ba914e', width=3)
    for i, line in enumerate(lines):
        draw.text((90, 285 + i*76), line, font=font(32), fill='#eee4d6')
    image.save(ROOT / 'artwork' / name)

if __name__ == '__main__':
    (ROOT / 'artwork').mkdir(exist_ok=True)
    board('exchange.png', 'CHOAM EXCHANGE', 'OFFICIAL GUILD TRADE', [
        'SOLARIS  /  SPICE  /  CONTRACTS', 'Speak to the Exchange Broker.',
        'Planetary markets follow server rules.', 'Discreet supplies: follow the side passage.'])
    board('news.png', 'GUILD DISPATCH', 'NEWS & ARRIVALS', [
        'Welcome aboard the Spacing Guild.', 'Five worlds. One starport.',
        'Landing apron: arrivals. Launch pads: departures.', 'Read the lecterns for current notices.',
        'Official exchange and discreet market are open.'])
    board('rules.png', 'GUILD CHARTER', 'SAFE PASSAGE FOR EVERY TRAVELLER', [
        'Respect fellow travellers and staff.', 'No griefing, stealing, cheating or exploits.',
        'Keep chat welcoming. No harassment or spam.', 'Observe faction and trading rules.',
        'Read the lecterns for the full server charter.'])
