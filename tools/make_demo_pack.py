#!/usr/bin/env python3
"""Create five original synthetic demo loops and a manifest; no third-party audio."""
import json, math, struct, wave, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'

def main():
    (ASSETS / 'demo').mkdir(parents=True, exist_ok=True)
    worlds = [
        dict(worldId='neon-1986', displayName='Neon Coast',year='1986',universe='Demo',accentColor='#65E9FF',secondaryColor='#F36BCD'),
        dict(worldId='sunset-1992',displayName='Sunset District',year='1992',universe='Demo',accentColor='#FFC36A',secondaryColor='#B18355'),
        dict(worldId='metro-2001',displayName='Metro City',year='2001',universe='Demo',accentColor='#A4CDDF',secondaryColor='#7285AF')]
    stations=[]
    for i,(name,fm,genre,world) in enumerate([
        ('Neon FM','105.6','Synth demo',0),('Afterglow','98.7','Ambient demo',0),
        ('Coastline','103.0','Pulse demo',0),('Sunset Groove','101.2','Bass demo',1),('Night Signal','94.4','Keys demo',2)]):
        seconds=24+i*4; rate=22050; path=ASSETS/'demo'/f'station-{i}.wav'
        with wave.open(str(path),'wb') as out:
            out.setparams((1,2,rate,0,'NONE','not compressed'))
            data=bytearray()
            notes=[220,261.6256,329.6276,391.9954,329.6276,261.6256,293.6648,246.9417]
            for sample in range(seconds*rate):
                t=sample/rate; note=notes[int(t*2+i)%len(notes)]*(0.5 if i==3 else 1)
                phase=t%0.5; env=min(1,phase/.02)*math.exp(-phase*5)
                fade=min(1,t/.03,(seconds-t)/.03)
                value=fade*(.13*env*math.sin(2*math.pi*note*t)+.05*math.sin(2*math.pi*55*(i+1)*t))
                data.extend(struct.pack('<h',int(32767*value)))
            out.writeframes(data)
        stations.append(dict(stationId=f'demo-{i}',worldId=worlds[world]['worldId'],displayName=name,frequency=fm,
            genre=genre,audioSource='pack',audioPath=f'demo/station-{i}.wav',durationMs=seconds*1000,
            dailySeedEnabled=True,offsetMs=i*1379))
    manifest=dict(packId='open-city-demo',packVersion='1',worlds=worlds,stations=stations)
    (ASSETS/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    (ROOT/'sample-pack').mkdir(exist_ok=True)
    (ROOT/'sample-pack/manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    destination=ROOT/'demo-pack.zip'
    with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED) as z:
        z.write(ASSETS/'manifest.json','manifest.json')
        for p in sorted((ASSETS/'demo').glob('*.wav')): z.write(p,p.relative_to(ASSETS))
    print(f'Generated {len(stations)} demo stations: {destination}')
if __name__ == '__main__': main()
