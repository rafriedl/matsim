/* *********************************************************************** *
 * project: org.matsim.*
 * PassengerAccessEgressImpl
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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
package org.matsim.core.mobsim.qsim.pt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.core.api.experimental.events.BoardingDeniedEvent;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.MobsimDriverAgent;
import org.matsim.core.mobsim.framework.PassengerAgent;
import org.matsim.core.mobsim.qsim.InternalInterface;
import org.matsim.core.mobsim.qsim.agents.PersonDriverAgentImpl;
import org.matsim.core.mobsim.qsim.interfaces.MobsimVehicle;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;


/**
 * 
 * @author dgrether
 *
 */
class PassengerAccessEgressImpl implements PassengerAccessEgress {

	private InternalInterface internalInterface;
	private TransitStopAgentTracker agentTracker;
	private final boolean isGeneratingDeniedBoardingEvents ;
	private Set<PTPassengerAgent> agentsDeniedToBoard = null; 
	
	PassengerAccessEgressImpl(InternalInterface internalInterface, TransitStopAgentTracker agentTracker) {
		this.internalInterface = internalInterface;
		this.agentTracker = agentTracker;
		if ( this.internalInterface != null ) {
			this.isGeneratingDeniedBoardingEvents = 
					this.internalInterface.getMobsim().getScenario().getConfig().vspExperimental().isGeneratingBoardingDeniedEvents() ;
			if (this.isGeneratingDeniedBoardingEvents){
				this.agentsDeniedToBoard = new HashSet<PTPassengerAgent>();
			}
		} else {
			this.isGeneratingDeniedBoardingEvents = false ;
		}
	}
	
	/**
	 * @return should be 0.0 or 1.0, values greater than 1.0 may lead to buggy behavior, dependent on TransitStopHandler used
	 */
	/*package*/ double calculateStopTimeAndTriggerBoarding(TransitRoute transitRoute, TransitLine transitLine, final TransitVehicle vehicle, 
			final TransitStopFacility stop, List<TransitRouteStop> stopsToCome, final double now) {
		ArrayList<PTPassengerAgent> passengersLeaving = findPassengersLeaving(vehicle, stop);
		int freeCapacity = vehicle.getPassengerCapacity() -  vehicle.getPassengers().size() + passengersLeaving.size();
		List<PTPassengerAgent> passengersEntering = findPassengersEntering(transitRoute, transitLine, vehicle, stop, stopsToCome, freeCapacity, now);
		
		TransitStopHandler stopHandler = vehicle.getStopHandler();
		double stopTime = stopHandler.handleTransitStop(stop, now, passengersLeaving, passengersEntering, this, vehicle);
		if (stopTime == 0.0){ // (de-)boarding is complete when the additional stopTime is 0.0
			if (this.isGeneratingDeniedBoardingEvents){
				this.fireBoardingDeniedEvents(vehicle, now);
				this.agentsDeniedToBoard.clear();
			}
		}
		return stopTime;
	}

	private void fireBoardingDeniedEvents(TransitVehicle vehicle, double now){
		Id vehicleId = vehicle.getId() ;
		for (PTPassengerAgent agent : this.agentsDeniedToBoard){
			Id agentId = agent.getId() ;
			this.internalInterface.getMobsim().getEventsManager().processEvent(
					new BoardingDeniedEvent(now, agentId, vehicleId)
					) ;
		}
	}
	
	
	private List<PTPassengerAgent> findPassengersEntering(TransitRoute transitRoute, TransitLine transitLine, TransitVehicle vehicle, 
			final TransitStopFacility stop, List<TransitRouteStop> stopsToCome, int freeCapacity, double now) {
		ArrayList<PTPassengerAgent> passengersEntering = new ArrayList<PTPassengerAgent>();
		for (PTPassengerAgent agent : this.agentTracker.getAgentsAtStop(stop.getId())) {
			if ( !this.isGeneratingDeniedBoardingEvents ) {
				if (freeCapacity == 0) {
					break;
				}
			}
			if (agent.getEnterTransitRoute(transitLine, transitRoute, stopsToCome, vehicle)) {
				if ( !this.isGeneratingDeniedBoardingEvents ) {
					// this tries to leave the pre-existing code intact; thus the replication of code below
					passengersEntering.add(agent);
					freeCapacity--;
				} 
				else {
					if ( freeCapacity >= 1 ) {
						passengersEntering.add(agent);
						freeCapacity--;
					} 
					else {
						this.agentsDeniedToBoard.add(agent);
					}
				}
			}
		}
		return passengersEntering;
	}
	
	
	
	private ArrayList<PTPassengerAgent> findPassengersLeaving(TransitVehicle vehicle,
			final TransitStopFacility stop) {
		ArrayList<PTPassengerAgent> passengersLeaving = new ArrayList<PTPassengerAgent>();
		for (PassengerAgent passenger : vehicle.getPassengers()) {
			if (((PTPassengerAgent) passenger).getExitAtStop(stop)) {
				passengersLeaving.add((PTPassengerAgent) passenger);
			}
		}
		return passengersLeaving;
	}
	

	@Override
	public boolean handlePassengerEntering(PTPassengerAgent passenger, MobsimVehicle vehicle,  Id fromStopFacilityId, double time) {
		boolean handled = vehicle.addPassenger(passenger);
		if(handled){
			this.agentTracker.removeAgentFromStop(passenger, fromStopFacilityId);
			MobsimAgent planAgent = (MobsimAgent) passenger;
			if (planAgent instanceof PersonDriverAgentImpl) { 
				Id agentId = planAgent.getId();
				Id linkId = planAgent.getCurrentLinkId();
				this.internalInterface.unregisterAdditionalAgentOnLink(agentId, linkId) ;
			}
			MobsimDriverAgent agent = (MobsimDriverAgent) passenger;
			EventsManager events = this.internalInterface.getMobsim().getEventsManager();
			events.processEvent(new PersonEntersVehicleEvent(time, agent.getId(), vehicle.getVehicle().getId()));
		}
		return handled;
	}

	@Override
	public boolean handlePassengerLeaving(PTPassengerAgent passenger, MobsimVehicle vehicle, Id toLinkId, double time) {
		boolean handled = vehicle.removePassenger(passenger);
		if(handled){
//			MobsimDriverAgent agent = (MobsimDriverAgent) passenger;
			EventsManager events = this.internalInterface.getMobsim().getEventsManager();
			events.processEvent(new PersonLeavesVehicleEvent(time, passenger.getId(), vehicle.getVehicle().getId()));
			
			// from here on works only if PassengerAgent can be cast into MobsimAgent ... but this is how it was before.
			// kai, sep'12
			
			MobsimAgent agent = (MobsimAgent) passenger ;
			agent.notifyArrivalOnLinkByNonNetworkMode(toLinkId);
			agent.endLegAndComputeNextState(time);
			this.internalInterface.arrangeNextAgentState(agent) ;
			// (cannot set trEngine to TransitQSimEngine because there are tests where this will not work. kai, dec'11)
		}
		return handled;
	}





}
