"""Reproduce the bundled display LUT pack from hash-pinned creator sources."""
import argparse
import hashlib
import json
import math
import pathlib
import re
import struct
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'


def package(input_directory=None):
    sources = json.loads((ROOT / 'docs/online-lut-sources.json').read_text())
    config_path = ASSETS / 'luts/config.json'
    config = json.loads(config_path.read_text())
    entries = [x for x in config['luts'] if not x['id'].startswith('online_')]
    results = []
    for index, source in enumerate(sources):
        if input_directory:
            raw = (pathlib.Path(input_directory) / f'{index}.cube').read_bytes()
        else:
            url = f"https://raw.githubusercontent.com/{source['repo']}/{source['ref']}/" + urllib.parse.quote(source['path'])
            with urllib.request.urlopen(url, timeout=60) as response:
                raw = response.read(16 * 1024 * 1024)
        git_hash = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
        assert git_hash == source['sha'], f"Source hash mismatch: {source['path']}"
        size = 0
        data = []
        comments = []
        for line in raw.decode('utf-8-sig').splitlines():
            line = line.strip()
            if not line:
                continue
            if line.startswith('#'):
                comments.append(line)
                continue
            if line.startswith('TITLE'):
                continue
            if line.startswith('LUT_3D_SIZE'):
                assert not size
                size = int(line.split()[1])
            elif line.startswith('DOMAIN_MIN'):
                assert list(map(float, line.split()[1:])) == [0, 0, 0]
            elif line.startswith('DOMAIN_MAX'):
                assert list(map(float, line.split()[1:])) == [1, 1, 1]
            else:
                values = list(map(float, line.split()))
                assert len(values) == 3 and all(math.isfinite(x) and 0 <= x <= 1 for x in values)
                data.extend(values)
        assert 2 <= size <= 65 and len(data) == 3 * size ** 3
        name = pathlib.PurePosixPath(source['path']).stem
        if source['author'] == 'slay':
            name = 'slay ' + name.split('-')[-1].title()
            category = 'slay Cinema'
        else:
            category = 'Striped Purple'
        slug = re.sub('[^a-z0-9]+', '_', name.lower()).strip('_')
        lut_id = 'online_' + slug
        relative = f'online/{slug}.plut'
        # v3: magic, version, grid, RGB16 type, sRGB transfer ID, sRGB gamut ordinal.
        quantized = [int(x * 65535 + 0.5) for x in data]
        payload = struct.pack('<4s5I', b'PLUT', 3, size, 1, 0, 0)
        payload += struct.pack('<' + str(len(quantized)) + 'H', *quantized)
        destination = ASSETS / 'luts' / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(payload)
        decoded = struct.unpack('<' + str(len(data)) + 'H', payload[24:])
        error = max(abs(x - y / 65535) for x, y in zip(data, decoded))
        assert error <= 0.5 / 65535 + 1e-12
        entries.append(dict(id=lut_id, name={'en': name, 'zh': name}, path=relative,
                            category=category, isVip=False, isDefault=False))
        results.append(dict(id=lut_id, name=name, grid=size, source=source,
                            original_comments=comments, input='sRGB display RGB',
                            input_note=('Display-photo integration assumption; upstream does not specify a profile.'
                                        if source['author'] != 'slay' else 'Creative display-photo look; not Log conversion.'),
                            sha256=hashlib.sha256(payload).hexdigest(), bytes=len(payload),
                            max_quantization_error=error))
    assert len({x['id'] for x in entries}) == len(entries)
    config['luts'] = entries
    config_path.write_text(json.dumps(config, ensure_ascii=False, indent=2) + '\n')
    (ASSETS / 'licenses/online-lut-provenance.json').write_text(json.dumps(results, indent=2) + '\n')
    print(json.dumps({'packaged': len(results), 'bytes': sum(x['bytes'] for x in results),
                      'max_quantization_error': max(x['max_quantization_error'] for x in results)}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input-directory', help='Previously downloaded hash-verified CUBE files, numbered from zero.')
    package(parser.parse_args().input_directory)
