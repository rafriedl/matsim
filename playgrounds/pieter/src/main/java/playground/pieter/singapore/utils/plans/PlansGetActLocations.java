package playground.pieter.singapore.utils.plans;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import others.sergioo.util.dataBase.DataBaseAdmin;
import others.sergioo.util.dataBase.NoConnectionException;

public class PlansGetActLocations {
	void run(Population plans, DataBaseAdmin dba, String tableName) {
		System.out.println("    running " + this.getClass().getName()
				+ " algorithm...");
		int counter=0;
		try {
			dba.executeStatement("drop table if exists " + tableName);
			dba.executeStatement("create table "
					+ tableName
					+ "(full_pop_pid varchar(45),facility_id varchar(45), activity varchar(45)) ");
            for (Id<Person> personId : plans.getPersons().keySet()) {
                Person person = plans.getPersons().get(
                        personId);

                for (int i = person.getPlans().size() - 1; i >= 0; i--) {
                    Plan plan = person.getPlans().get(i);
                    for (int j = 0; j < plan.getPlanElements().size(); j += 2) {
                        Activity act = (Activity) plan
                                .getPlanElements().get(j);
                        if (!act.getType().equals("home") && !act.getType().equals("pt interaction")) {
                            dba.executeUpdate(String
                                    .format("insert into %s values (\'%s\',\'%s\',\'%s\');",
                                            tableName, personId.toString(),
                                            act.getFacilityId(), act.getType()));
                        }

                    }

                }
                counter++;
                if (counter % 1000 == 0) {
                    Logger.getLogger("PGA").info("Handled " + counter + " plans...");
                }

            }
		} catch (SQLException | NoConnectionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        System.out.println("    done.");

	}
	public static void main(String[] args) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException, SQLException{
		Scenario scenario;
		MatsimRandom.reset(123);
		scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		new MatsimNetworkReader(scenario.getNetwork()).readFile("data/singapore6.xml");
		new PopulationReader(scenario)
				.readFile("data/sing2PlansFilteredUnroutedReduced.xml");
//		.readFile("data/short_plans.xml");
		Population pop = scenario.getPopulation();
		DataBaseAdmin dba = new DataBaseAdmin(new File("data/matsim2.properties"));
		new PlansGetActLocations().run(pop, dba, "activity_location_results");
	}
}
