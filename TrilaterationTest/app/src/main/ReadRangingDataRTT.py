import math
import os

def getResource(filename):
    return os.path.join(os.path.dirname(__file__), './resources/', filename)

measurements = []
with open(getResource('RangingDataAll.txt'), 'r') as f:
    currentPosition = ''
    for line in f:
        data = line.strip().split('\t')
        if data[-3] != 'TRUE':
            continue
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
        best[-1].append(min(m, key = lambda s: int(s[12])))

with open(getResource('RTTOneFloor.txt'), 'w') as f:
    for b in best[:19]: # the first 19 records are for the one floor experiment
        for m in b:
            f.write('\t'.join(m[3:9])+'\t'+m[10]+'\n')
        f.write("\n")
    f.write("\n")

with open(getResource('RTTTwoFloors.txt'), 'w') as f:
    for b in best[19:]: # the first 19 records are for the one floor experiment, the others for the two floor experiment
        for m in b:
            f.write('\t'.join(m[3:9])+'\t'+m[10]+'\n')
        f.write("\n")
    f.write("\n")
