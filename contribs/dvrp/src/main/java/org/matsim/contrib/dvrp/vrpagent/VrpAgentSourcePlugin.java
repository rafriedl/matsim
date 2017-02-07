package org.matsim.contrib.dvrp.vrpagent;

import java.util.*;

import org.matsim.contrib.dvrp.data.Fleet;
import org.matsim.contrib.dvrp.optimizer.VrpOptimizer;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentLogic.DynActionCreator;
import org.matsim.core.config.Config;
import org.matsim.core.mobsim.framework.AgentSource;
import org.matsim.core.mobsim.qsim.*;
import org.matsim.vehicles.VehicleType;

import com.google.inject.*;
import com.google.inject.name.Names;


public class VrpAgentSourcePlugin
    extends AbstractQSimPlugin
{
    private final String mode;


    public VrpAgentSourcePlugin(Config config, String mode)
    {
        super(config);
        this.mode = mode;
    }


    @Override
    public Collection<? extends Module> modules()
    {
        Collection<Module> result = new ArrayList<>();
        result.add(new AbstractModule() {
            @Override
            public void configure()
            {
                bind(VrpAgentSource.class).annotatedWith(Names.named(mode))
                        .toProvider(new VrpAgentSourceProvider(mode));
            }
        });
        return result;
    }


    @Override
    public Collection<Class<? extends AgentSource>> agentSources()
    {
        Collection<Class<? extends AgentSource>> result = new ArrayList<>();
        result.add(VrpAgentSource.class);
        return result;
    }


    public static class VrpAgentSourceProvider
        implements Provider<VrpAgentSource>
    {
        private final String mode;

        @Inject
        private Injector injector;

        @Inject
        private QSim qSim;


        public VrpAgentSourceProvider(String mode)
        {
            this.mode = mode;
        }


        @Override
        public VrpAgentSource get()
        {
            DynActionCreator nextActionCreator = injector
                    .getInstance(Key.get(DynActionCreator.class, Names.named(mode)));
            Fleet fleet = injector.getInstance(Key.get(Fleet.class, Names.named(mode)));
            VrpOptimizer optimizer = injector
                    .getInstance(Key.get(VrpOptimizer.class, Names.named(mode)));
            VehicleType vehicleType = injector
                    .getInstance(Key.get(VehicleType.class, Names.named(mode)));

            return new VrpAgentSource(nextActionCreator, fleet, optimizer, qSim, vehicleType);
        }
    }
}
