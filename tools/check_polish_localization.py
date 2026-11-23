"""Kontrola integralności lokalizacji PL, nie automatyczna ocena jej stylu.

Uruchomienie: python3 tools/check_polish_localization.py
"""
from pathlib import Path
import re
import unicodedata
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TOKEN = re.compile(r"%(?:\d+\$)?[sdfoxegcba]|%%", re.I)
OLD_TERMS = ("rozmowa o tekście", "rozmowie o tekście", "autozdjęcie",
             "zapytaj znajduje stronę", "tryb biały")


def resources(path):
    root = ET.parse(path).getroot()
    keys = [e.attrib['name'] for e in root]
    assert len(keys) == len(set(keys)), f"Powtórzone klucze: {path}"
    return {e.attrib['name']: e for e in root}


def text(element):
    return ''.join(element.itertext())


def validate(en, pl):
    assert en.keys() == pl.keys(), f"Różne klucze: {en.keys() ^ pl.keys()}"
    for key, source in en.items():
        translated = pl[key]
        assert source.tag == translated.tag, key
        if source.tag == 'plurals':
            assert {e.get('quantity') for e in translated} == {'one', 'few', 'many', 'other'}, key
            reference = source.find("item[@quantity='other']")
            for item in translated:
                assert set(TOKEN.findall(text(item))) == set(TOKEN.findall(text(reference))), key
        else:
            assert set(TOKEN.findall(text(source))) == set(TOKEN.findall(text(translated))), key
        value = text(translated)
        assert value.strip(), key
        assert not any(term in value.lower() for term in OLD_TERMS), key
        assert not any(unicodedata.category(c) == 'Cf' or c == '\ufffd' for c in value), key
    assert text(pl['help_title_text_chat']) == 'Rozmowa tekstowa'
    assert text(pl['help_title_image_chat']) == 'Rozmowa o zdjęciu'
    for label_key, body_key in (
        ('bar_auto_capture', 'help_buttons_image_chat'),
        ('settings_ask_finds_page', 'help_body_text_chat'),
        ('chat_input_find_hint', 'help_buttons_find_objects'),
        ('chat_input_hint', 'help_buttons_text_chat'),
    ):
        assert text(pl[label_key]) in text(pl[body_key]), (label_key, body_key)


def main():
    en = resources(ROOT / 'app/src/main/res/values/strings.xml')
    pl = resources(ROOT / 'app/src/main/res/values-pl/strings.xml')
    validate(en, pl)
    fi = resources(ROOT / 'app/src/main/res/values-fi/strings.xml')
    inherited_missing = {'help_buttons_find_objects', 'help_buttons_image_chat',
                         'help_buttons_mute', 'help_common_moving', 'help_common_top'}
    assert en.keys() - fi.keys() == inherited_missing
    assert not (fi.keys() - en.keys())
    for key, translated in fi.items():
        source = en[key]
        assert source.tag == translated.tag, key
        if source.tag == 'plurals':
            reference = source.find("item[@quantity='other']")
            assert {e.get('quantity') for e in translated} == {'one', 'other'}, key
            for item in translated:
                assert set(TOKEN.findall(text(item))) == set(TOKEN.findall(text(reference))), key
        else:
            assert set(TOKEN.findall(text(source))) == set(TOKEN.findall(text(translated))), key
        assert text(translated).strip(), key
    print('FI: 5 odziedziczonych braków, nowe klucze kompletne; formaty poprawne.')
    # Kontrole negatywne: przyrząd musi odrzucać dawną nazwę i zepsuty placeholder.
    for key, replacement in (
        ('help_title_text_chat', 'Rozmowa o tekście'),
        ('settings_version', 'Wersja %1$s (%2$s)'),
    ):
        original = pl[key].text
        pl[key].text = replacement
        try:
            validate(en, pl)
        except AssertionError:
            pass
        else:
            raise AssertionError(f'Kontrola negatywna nie zadziałała: {key}')
        finally:
            pl[key].text = original
    validate(en, pl)
    print(f'PASS: {len(pl)} zasobów EN/PL; klucze, placeholdery, polskie liczby mnogie, nazwy w pomocy i znaki Unicode.')
    print('PASS: 2 kontrole negatywne (dawna nazwa i uszkodzony placeholder).')
    print('Styl i wierność znaczeniową ocenia się osobno przez przeczytanie tekstów w kontekście funkcji.')


if __name__ == '__main__':
    main()
