/* *********************************************************************** *
 * project: org.matsim.*
 * EmissionCostModule.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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
package playground.vsp.airPollution.flatEmissions;

import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.contrib.emissions.types.ColdPollutant;
import org.matsim.contrib.emissions.types.WarmPollutant;


/**
 * @author benjamin
 *
 */
public class EmissionCostModule {
	private static final Logger logger = Logger.getLogger(EmissionCostModule.class);
	
	private final double emissionCostFactor;
	private boolean considerCO2Costs = false;
	
	/*Values taken from IMPACT (Maibach et al.(2008))*/
	// following is not required any more, using EmissionCostFactors instead, amit Nov 16.
//	private final double EURO_PER_GRAMM_NOX = 9600. / (1000. * 1000.);
//	private final double EURO_PER_GRAMM_NMVOC = 1700. / (1000. * 1000.);
//	private final double EURO_PER_GRAMM_SO2 = 11000. / (1000. * 1000.);
//	private final double EURO_PER_GRAMM_PM2_5_EXHAUST = 384500. / (1000. * 1000.);
//	private final double EURO_PER_GRAMM_CO2 = 70. / (1000. * 1000.);


	public EmissionCostModule(double emissionCostFactor, boolean considerCO2Costs) {
		this.emissionCostFactor = emissionCostFactor;
		logger.info("Emission costs from Maibach et al. (2008) are multiplied by a factor of " + this.emissionCostFactor);
		
		if(considerCO2Costs){
			this.considerCO2Costs = true;
			logger.info("CO2 emission costs will be calculated... ");
		} else {
			logger.info("CO2 emission costs will NOT be calculated... ");
		}
	}
	
	public EmissionCostModule(double emissionCostFactor) {
		this.emissionCostFactor = emissionCostFactor;
		logger.info("Emission costs from Maibach et al. (2008) are multiplied by a factor of " + this.emissionCostFactor);
		logger.info("CO2 emission costs will NOT be calculated... ");
	}

	public double calculateWarmEmissionCosts(Map<WarmPollutant, Double> warmEmissions) {
		double warmEmissionCosts = 0.0;
		
		for(WarmPollutant wp : warmEmissions.keySet()){
			if(wp.equals(WarmPollutant.CO2_TOTAL) && ! considerCO2Costs) {
				// do nothing
			} else {
				double costFactor = EmissionCostFactors.getCostFactor(wp.toString());
				warmEmissionCosts += warmEmissions.get(wp) * costFactor ;
			}
		}
		return this.emissionCostFactor * warmEmissionCosts;
	}
	
	public double calculateColdEmissionCosts(Map<ColdPollutant, Double> coldEmissions) {
		double coldEmissionCosts = 0.0;
		
		for(ColdPollutant cp : coldEmissions.keySet()){
			double costFactor = EmissionCostFactors.getCostFactor(cp.toString());
			coldEmissionCosts += costFactor * coldEmissions.get(cp);
		}
		return this.emissionCostFactor * coldEmissionCosts;
	}

}
