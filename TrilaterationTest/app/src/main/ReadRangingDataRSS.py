import math
import os

def getResource(filename):
    return os.path.join(os.path.dirname(__file__), './resources/', filename)

accessPoints = { # BSSID: [RSS at one meter, n estimate one floor, n estimate two floors]
    '08:b4:b1:85:11:61': [-42, 2.285191613, 2.407779215],
    '08:b4:b1:85:12:95': [-40, 2.79342456, 3.431905356],
    '08:b4:b1:85:12:ab': [-41, 2.791738071, 3.566292808],
    '08:b4:b1:85:14:21': [-40, 2.861091896, 3.18002304],
    '08:b4:b1:85:2e:57': [-41, 2.499801535, 3.308775049],
    '08:b4:b1:85:4a:f5': [-41, 2.416460212, 2.552698166],
}

measurements = []
with open(getResource('RangingDataAll.txt'), 'r') as f:
    currentPosition = ''
    for line in f:
        data = line.strip().split('\t')
        if data[3] != currentPosition:
            currentPosition = data[3]
            measurements.append([])
        measurements[-1].append(data)

best = []
for x in measurements:
    d = {}
    for m in x:
        if m[2] not in d:
            d[m[2]] = []
        d[m[2]].append(m)
    best.append([])
    for m in d.values():
        best[-1].append(m[0])

with open(getResource('RSSOneFloor.txt'), 'w') as f:
    for b in best[:19]: # the first 19 records are for the one floor experiment
        for m in b:
            ap = accessPoints[m[2]]
            distance = 1000*pow(10, (ap[0]-int(m[-2]))/(10*ap[1]))
            f.write('\t'.join(m[3:9])+'\t'+str(distance)+'\n')
        f.write("\n")
    f.write("\n")

with open(getResource('RSSTwoFloors.txt'), 'w') as f:
    for b in best[19:]: # the first 19 records are for the one floor experiment, the others for the two floor experiment
        for m in b:
            ap = accessPoints[m[2]]
            distance = 1000*pow(10, (ap[0]-int(m[-2]))/(10*ap[2]))
            f.write('\t'.join(m[3:9])+'\t'+str(distance)+'\n')
        f.write("\n")
    f.write("\n")

