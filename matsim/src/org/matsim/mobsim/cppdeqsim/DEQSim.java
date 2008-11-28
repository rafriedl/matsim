/* *********************************************************************** *
 * project: org.matsim.*
 * DEQSim.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.mobsim.cppdeqsim;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;

import org.matsim.analysis.IterationStopWatch;
import org.matsim.config.Config;
import org.matsim.config.ConfigWriter;
import org.matsim.config.Module;
import org.matsim.events.Events;
import org.matsim.gbl.Gbl;
import org.matsim.mobsim.queuesim.ExternalMobsim;
import org.matsim.network.Link;
import org.matsim.population.Act;
import org.matsim.population.Leg;
import org.matsim.population.Person;
import org.matsim.population.Plan;
import org.matsim.population.Population;
import org.matsim.population.routes.CarRoute;
import org.matsim.utils.misc.Time;

/**
 * Calls David Charypar's DEQSim after writing out all the relevant plans, and reading in
 * the events generated by the external simulation afterwards.
 *
 * Supports writing the plans and reading the events in a non-standard binary format to
 * reduce the amount of data written to/read from disk to speed the file I/O up.
 *
 * @author mrieser
 */
public class DEQSim extends ExternalMobsim {

	private static final String CONFIG_MODULE = "deqsim";

	private static final boolean USE_BINARY_PLANS = true;

	private IterationStopWatch stopwatch = null;

	public DEQSim(final Population population, final Events events) {
		super(population, events);
	}

	@Override
	protected void init() {
		if (DEQSim.USE_BINARY_PLANS) {
			this.plansFileName = "deq_plans.dat";
		} else {
			this.plansFileName = "deq_plans.xml";
		}
		this.eventsFileName = "deq_events.dat";
		this.configFileName = "deq_config.xml";

		this.executable = Gbl.getConfig().getParam(DEQSim.CONFIG_MODULE, "executable");
	}

	public void setIterationStopWatch(final IterationStopWatch stopwatch) {
		this.stopwatch = stopwatch;
	}

	@Override
	protected void writeConfig(final String iterationPlansFile, final String iterationEventsFile, final String iterationConfigFile) throws FileNotFoundException, IOException {
		Config simConfig = Gbl.getConfig();
		System.out.println("writing deqsim-config at " + (new Date()));
		Config deqConfig = new Config();
		// network
		Module module = deqConfig.createModule("network");
		module.addParam("inputNetworkFile", simConfig.network().getInputFile());
		module.addParam("localInputDTD", "dtd/matsim_v1.dtd");
		// green time fractions
		module = deqConfig.createModule("greentimefractions");
		module.addParam("inputGTFfile",simConfig.findParam("greentimefractions","inputGTFfile"));
		// plans
		module = deqConfig.createModule("plans");
		module.addParam("inputPlansFile", iterationPlansFile);
		if (DEQSim.USE_BINARY_PLANS) {
			module.addParam("inputVersion", "matsimDEQv1");
		} else {
			module.addParam("inputVersion", "matsimXMLv4");
		}
		// events
		module = deqConfig.createModule("events");
		module.addParam("outputFile", iterationEventsFile);
		module.addParam("outputFormat", "matsimDEQ1");
		// deqsim
		module = deqConfig.createModule(DEQSim.CONFIG_MODULE);
		module.addParam("startTime", simConfig.getParam(DEQSim.CONFIG_MODULE, "startTime"));
		String endTime = simConfig.getParam(DEQSim.CONFIG_MODULE, "endTime");
		if (endTime.equals("00:00:00")) endTime = "30:00:00"; // deqsim seems not to support an open endtime
		module.addParam("endTime", endTime);
		module.addParam("flowCapacityFactor", simConfig.getParam(DEQSim.CONFIG_MODULE, "flowCapacityFactor"));
		module.addParam("storageCapacityFactor", simConfig.getParam(DEQSim.CONFIG_MODULE, "storageCapacityFactor"));
		module.addParam("squeezeTime", simConfig.getParam(DEQSim.CONFIG_MODULE, "squeezeTime"));
		module.addParam("carSize", simConfig.getParam(DEQSim.CONFIG_MODULE, "carSize"));
		module.addParam("gapTravelSpeed", simConfig.getParam(DEQSim.CONFIG_MODULE, "gapTravelSpeed"));

		PrintWriter writer = new PrintWriter(new File(iterationConfigFile));

		ConfigWriter configwriter = new ConfigWriter(deqConfig, writer);
		configwriter.write();
		writer.flush();
		writer.close();
	}

	@Override
	protected void writePlans(final String iterationPlansFile) throws FileNotFoundException, IOException {
		System.out.println("start writing plans for deqsim. " + (new Date()));
		if (this.stopwatch != null) {
			this.stopwatch.beginOperation("write deqsim plans");
		}
		if (DEQSim.USE_BINARY_PLANS) {
			DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(iterationPlansFile)));
			// # persons, int, 32bit
			out.writeInt(this.population.getPersons().size());
			// for each person...
			for (Person person : this.population.getPersons().values()) {
				writePerson(out, person);
			}
			out.close();
		} else {
			super.writePlans(iterationPlansFile);
		}
		if (this.stopwatch != null) {
			this.stopwatch.endOperation("write deqsim plans");
		}
		System.out.println("done writing plans for deqsim. " + (new Date()));
	}

	private void writePerson(final DataOutputStream out, final Person person) throws IOException {
		// person id, int, 32bit
		out.writeInt(Integer.parseInt(person.getId().toString()));

		Plan plan = person.getSelectedPlan();
		// # legs, int, 32bit
		out.writeInt((plan.getActsLegs().size()-1) / 2);

		Act nextAct = null;
		if (plan.getActsLegs().size() > 2) {
			// we have at least one leg
			nextAct = (Act) plan.getActsLegs().get(0);
		}

		// for each leg...
		double time = 0;
		for (int i = 1, max = plan.getActsLegs().size(); i < max; i += 2) {
			Leg leg = (Leg) plan.getActsLegs().get(i);
			Act act = nextAct;
			nextAct = (Act) plan.getActsLegs().get(i+1);

			// TODO [MR] this is functionality that's regularly used, maybe generalize it?
			// also see below
			if (act.getEndTime() != Time.UNDEFINED_TIME && act.getDuration() != Time.UNDEFINED_TIME) {
				// use min (endtime, time + dur)
				time = Math.min(act.getEndTime(), time + act.getDuration());
			} else if (act.getEndTime() != Time.UNDEFINED_TIME) {
				// use endtime
				time = act.getEndTime();
			} else if (act.getDuration() != Time.UNDEFINED_TIME) {
				// use duration
				time += act.getDuration();
			} else {
				Gbl.errorMsg("endtime or duration must be specified!");
			}

			// departuretime, double, 64bit
			out.writeDouble(time);

			CarRoute route = (CarRoute) leg.getRoute();
			// in the binary format, we write the link-ids instead of the node-ids
			List<Link> linkRoute = route.getLinks();

			// # links, int, 32bit
			out.writeInt(linkRoute.size() + 2);
			// the first link where the departure happens
			out.writeInt(Integer.parseInt(act.getLink().getId().toString()));
			for (Link link : linkRoute) {
				// node id, int, 32bit
				out.writeInt(Integer.parseInt(link.getId().toString()));
			}
			// the last link where the next activity is
			out.writeInt(Integer.parseInt(nextAct.getLink().getId().toString()));

			// TODO [MR] see above
			if (leg.getTravelTime() != Time.UNDEFINED_TIME) {
				time += leg.getTravelTime();
			}

		}
	}

	@Override
	protected void runExe(final String iterationConfigFile) throws FileNotFoundException, IOException {
		if (this.stopwatch != null) {
			this.stopwatch.beginOperation("run deqsim");
		}
		super.runExe(iterationConfigFile);
		if (this.stopwatch != null) {
			this.stopwatch.endOperation("run deqsim");
		}
	}

	@Override
	protected void readEvents(final String iterationEventsFile) throws FileNotFoundException, IOException {
		if (this.stopwatch != null) {
			this.stopwatch.beginOperation("read deqsim events");
		}

		EventsReaderDEQv1 reader = new EventsReaderDEQv1(this.events);
		reader.readFile(iterationEventsFile);

		if (this.stopwatch != null) {
			this.stopwatch.endOperation("read deqsim events");
		}
	}

}
