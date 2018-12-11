#!/usr/bin/python

'''
A simple to compute the delivery ratio of the messages (with 95% CI) under 
various scenarios.
'''

import csv
#import gen_stats as gs
import math
import os


# Average of a list of numbers
def get_average(numbers = []):
	avg = 0.0
	n = len(numbers)
	for i in range(0, n):
		avg += numbers[i]
	if n > 0:
		avg /= n
	return avg


# Std. Dev. of a list of numbers
def get_std_dev(num = []):
	n = len(num)
	avg = get_average(num)
	
	variance = 0.0
	for i in range(0, n):
		variance += (num[i] - avg) ** 2
	if n > 0:
		variance /= n
	std = variance ** 0.5
	return std


# Get a named statistic from the MessageStats report file
def get_stat(file_name, stat_name):
	result = 0.0
	with open(file_name, 'r') as report:
		reader = csv.reader(report, delimiter = ' ')
		for line in reader:
			if line[0].find(stat_name) == 0:
				result = float(line[1])
				break
	
	return result


def get_energy_stat(file_name, time):
	# print("filename", file_name)
	available_energy = 0
	alive_nodes = -1
	with open(file_name, 'r') as report:
		reader = csv.reader(report, delimiter = ' ')
		for line in reader:
			if abs(float(line[0]) - float(time)) < 1 and line[1].find('available_energy') == 0:
				available_energy = float(line[2])
				alive_nodes = float(line[3])
				break
	
	# print("time", time, "energy", available_energy, "nodes", alive_nodes)
	return available_energy, alive_nodes


#Main starts here

folders = ('Nepal_Orig', 'Nepal_Bio')
routers = ('BioDRNRouter',)
endTimes = ('900', '1800', '2700', '3600',)
generators = ('MessageBurstGenerator',)
rngSeed =('1', )
# Scenario.name = %%Group.router%%_%%Scenario.endTime%%_%%Events1.class%%_%%MovementModel.rngSeed%%

save_folder = "Users/vijay/BioDRN_Code"
for folder in folders:
	print("\nFolder " + folder)
	for router in routers:
		for generator in generators:
			del_ratio = []
			latency = []
			hop_count = []
			overhead = []
			available_energy_list = []
			alive_nodes_list = []	
			for time in endTimes:
				for rng in rngSeed:
				    fname = "reports/%s/%s_%s_%s_%s_MessageStatsReport.txt" % (folder, router, time, generator, rng)
				    if os.path.isfile(fname):
				    	del_ratio.append(get_stat(fname, 'delivery_prob'))
				    	latency.append(get_stat(fname, "latency_avg"))
				    	hop_count.append(get_stat(fname, "hopcount_avg"))
				    	overhead.append(get_stat(fname, "overhead_ratio"))

				    else:
				    	print("Stat file not found", fname)

				    energyfname = "reports/%s/%s_%s_%s_%s_EnergyLevelReport.txt" % (folder, router, time, generator, rng)
				    if os.path.isfile(fname):
				    	available_energy, alive_nodes = get_energy_stat(energyfname, time)
				    	available_energy_list.append(available_energy)
				    	alive_nodes_list.append(alive_nodes)

				    else:
				    	print("Energy file not found", energyfname)

				# print("PDR", del_ratio)
				# print("Lat", latency)
				# print("Over", overhead)
				# print("Ener", available_energy_list)
				# print("nodes", alive_nodes_list)

				# Average delivery ratio
				avg_del = get_average(del_ratio)
				avg_latency = get_average(latency)
				avg_hop = get_average(hop_count)
				avg_overhead = get_average(overhead)
				avg_available_energy = get_average(available_energy_list)
				avg_alive_nodes = get_average(alive_nodes_list)

				#sd = get_std_dev(del_ratio)
				#ci = gs.confidence_interval_mean(rng_max, sd)

				print ('%s %s %.2f %.2f %.2f %.2f %.2f %.2f' % (generator, time, avg_del, avg_latency, avg_hop, avg_overhead, avg_available_energy, avg_alive_nodes))



