#!/usr/bin/env python3
"""Validate a pack without extracting it; reports metadata, local references and WAV duration."""
import json, sys, zipfile, wave, io
from pathlib import PurePosixPath

def validate(path):
    with zipfile.ZipFile(path) as z:
        names=z.namelist()
        assert len(names)==len(set(names)), 'Duplicate ZIP names'
        for n in names:
            p=PurePosixPath(n)
            assert not p.is_absolute() and '..' not in p.parts and '\\' not in n and ':' not in p.parts[0], f'Unsafe ZIP path: {n}'
        manifests=[n for n in names if PurePosixPath(n).name=='manifest.json']
        assert len(manifests)==1,'Exactly one manifest.json required'
        manifest=manifests[0]; base=PurePosixPath(manifest).parent
        data=json.loads(z.read(manifest))
        assert str(data['packId']).strip() and str(data['packVersion']).strip()
        worlds=data['worlds']; stations=data['stations']
        ids=[w['worldId'] for w in worlds]; station_ids=[s['stationId'] for s in stations]
        assert len(ids)==len(set(ids)) and ids
        assert len(station_ids)==len(set(station_ids)) and station_ids
        for s in stations:
            assert s['worldId'] in ids and s['displayName']
            for prefix in ('audio','logo','background'):
                source=s.get(prefix+'Source','pack')
                assert source in ('pack','zip','url','drive')
                if source in ('url','drive'):
                    assert s[prefix+'Url'].startswith('https://')
                else:
                    p=s.get(prefix+'Path','')
                    if not p and prefix!='audio': continue
                    assert p and '..' not in PurePosixPath(p).parts
                    name=str(base/p)
                    assert name in names, f'Missing {name}'
                    if prefix=='audio' and name.endswith('.wav'):
                        with wave.open(io.BytesIO(z.read(name))) as w:
                            duration=round(w.getnframes()*1000/w.getframerate())
                            assert duration==s.get('durationMs',duration), f'Duration mismatch: {name}'
        print(f"PASS: {data['packId']} · {len(worlds)} worlds · {len(stations)} stations · all local references valid")
if __name__=='__main__': validate(sys.argv[1])
