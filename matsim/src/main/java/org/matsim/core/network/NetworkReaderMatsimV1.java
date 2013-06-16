/* *********************************************************************** *
 * project: org.matsim.*
 * NetworkReaderMatsimV1.java
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

package org.matsim.core.network;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.core.utils.misc.StringUtils;
import org.matsim.core.utils.misc.Time;
import org.xml.sax.Attributes;

/**
 * A reader for network-files of MATSim according to <code>network_v1.dtd</code>.
 *
 * @author mrieser
 */
public class NetworkReaderMatsimV1 extends MatsimXmlParser {

	private final static String NETWORK = "network";
	private final static String LINKS = "links";
	private final static String NODE = "node";
	private final static String LINK = "link";

	private final Network network;
	private final Scenario scenario;

	private final static Logger log = Logger.getLogger(NetworkReaderMatsimV1.class);

	public NetworkReaderMatsimV1(final Scenario scenario) {
		super();
		this.scenario = scenario;
		this.network = scenario.getNetwork();
	}

	@Override
	public void startTag(final String name, final Attributes atts, final Stack<String> context) {
		if (NODE.equals(name)) {
			startNode(atts);
		} else if (LINK.equals(name)) {
			startLink(atts);
		} else if (NETWORK.equals(name)) {
			startNetwork(atts);
		} else if (LINKS.equals(name)) {
			startLinks(atts);
		}
	}

	@Override
	public void endTag(final String name, final String content, final Stack<String> context) {
		// currently, we do not have anything to do when a tag ends, maybe later sometimes...
	}

	private void startNetwork(final Attributes atts) {
		if (atts.getValue("type") != null) {
			log.info("Attribute 'type' is deprecated. There's always only ONE network, where the links and nodes define, which " +
					"transportation mode is allowed to use it (for the future)");
		}
		if (this.network instanceof NetworkImpl) {
			((NetworkImpl) this.network).setName(atts.getValue("name"));
			if (atts.getValue("capDivider") != null) {
				log.warn("capDivider defined. it will be used but should be gone eventually. " +
						"-- This is a weird comment, since the matsim public api tells to put this into the network rather than" +
						" into the ``links''.  kai, jun'11");
				String capperiod = atts.getValue("capDivider") + ":00:00";
				((NetworkImpl) this.network).setCapacityPeriod(Time.parseTime(capperiod));
			}
		}
	}

	private void startLinks(final Attributes atts) {
		double capacityPeriod = 3600.0; //the default of one hour
		if (this.network instanceof NetworkImpl) {
			String capperiod = atts.getValue("capperiod");
			if (capperiod != null) {
				capacityPeriod = Time.parseTime(capperiod);
			}
			else {
				log.warn("capperiod was not defined. Using default value of " + Time.writeTime(capacityPeriod) + ".");
			}
			((NetworkImpl) this.network).setCapacityPeriod(capacityPeriod);

			String effectivecellsize = atts.getValue("effectivecellsize");
			if (effectivecellsize == null){
				((NetworkImpl) this.network).setEffectiveCellSize(7.5); // we use a default cell size of 7.5 meters
			} else {
				((NetworkImpl) this.network).setEffectiveCellSize(Double.parseDouble(effectivecellsize));
			}

			String effectivelanewidth = atts.getValue("effectivelanewidth");
			if (effectivelanewidth == null) {
				((NetworkImpl) this.network).setEffectiveLaneWidth(3.75); // the default lane width is 3.75
			} else {
				((NetworkImpl) this.network).setEffectiveLaneWidth(Double.parseDouble(effectivelanewidth));
			}

			if ((atts.getValue("capPeriod") != null) || (atts.getValue("capDivider") != null) || (atts.getValue("capdivider") != null)) {
				log.warn("Found capPeriod, capDivider and/or capdivider in the links element.  They will be ignored, since they " +
						"should be set in the network element. -- This is a weird warning, since setting them in the " +
						"network element also produces a warning.");
				log.warn("At this point, it seems that, in network.xml, one sets capperiod in the `links' section, but in the " +
						"matsim api, the corresponding entry belongs into the `network' object. kai, jun'11") ;
			}
		}
	}

	private void startNode(final Attributes atts) {
		Node node = this.network.getFactory().createNode(this.scenario.createId(atts.getValue("id")), new CoordImpl(atts.getValue("x"), atts.getValue("y")));
		this.network.addNode(node);
		if (node instanceof NodeImpl) {
			((NodeImpl) node).setType(atts.getValue("type"));
			if (atts.getValue("origid") != null) {
				((NodeImpl) node).setOrigId(atts.getValue("origid"));
			}
		}
	}

	private void startLink(final Attributes atts) {
		Node fromNode = this.network.getNodes().get(this.scenario.createId(atts.getValue("from")));
		Node toNode = this.network.getNodes().get(this.scenario.createId(atts.getValue("to")));
		if (fromNode == null || toNode == null) {
			System.out.println("breakpoint");
		}
		Link l = this.network.getFactory().createLink(this.scenario.createId(atts.getValue("id")), fromNode, toNode);
		l.setLength(Double.parseDouble(atts.getValue("length")));
		l.setFreespeed(Double.parseDouble(atts.getValue("freespeed")));
		l.setCapacity(Double.parseDouble(atts.getValue("capacity")));
		l.setNumberOfLanes(Double.parseDouble(atts.getValue("permlanes")));
		this.network.addLink(l);
		if (l instanceof LinkImpl) {
			((LinkImpl) l).setOrigId(atts.getValue("origid"));
			((LinkImpl) l).setType(atts.getValue("type"));
		}
		if (atts.getValue("modes") != null) {
			String[] strModes = StringUtils.explode(atts.getValue("modes"), ',');
			if ((strModes.length == 1) && strModes[0].isEmpty()) {
				l.setAllowedModes(new HashSet<String>());
			} else {
				Set<String> modes = new HashSet<String>();
				for (int i = 0, n = strModes.length; i < n; i++) {
					modes.add(strModes[i].trim().intern());
				}
				l.setAllowedModes(modes);
			}
		}
		if (atts.getValue("volume") != null) {
			log.info("Attribute volume for element link is deprecated.");
		}
		if (atts.getValue("nt_category") != null) {
			log.info("Attribute nt_category for element link is deprecated.");
		}
		if (atts.getValue("nt_type") != null) {
			log.info("Attribute nt_type for element link is deprecated.");
		}
	}

}
