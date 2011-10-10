#! /usr/bin/env python

#: These colors, represented as RGB, will be included in the result.
POINTS = [(0, 0, 0), (85, 85, 85), (170, 170, 170), (255, 255, 255)]

result = []
steps = int(255 / (len(POINTS) - 1))
totalsteps = 0
added = set()

for j in xrange(len(POINTS) - 1):
    if j == len(POINTS) - 2:
        steps = 255 - totalsteps
    else:
        totalsteps += steps

    # Get the differences between this color and the next
    rdif = (POINTS[j + 1][0] - POINTS[j][0]) / steps
    gdif = (POINTS[j + 1][1] - POINTS[j][1]) / steps
    bdif = (POINTS[j + 1][2] - POINTS[j][2]) / steps

    for i in xrange(steps):
        r = POINTS[j][0] + round(i * rdif)
        g = POINTS[j][1] + round(i * gdif)
        b = POINTS[j][2] + round(i * bdif)
        toadd = '%d %d %d' % (r, g, b)
        if toadd in added:
            print "Duplicate color entry! Increase the range of colors, please."
            exit()
        else:
            result.append(toadd)

result.append('%d %d %d' % POINTS[j + 1])

print "\n".join(result)
