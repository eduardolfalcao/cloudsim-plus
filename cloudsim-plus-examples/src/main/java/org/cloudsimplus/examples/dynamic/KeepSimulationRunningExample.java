/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2018 Universidade da Beira Interior (UBI, Portugal) and
 *     the Instituto Federal de Educação Ciência e Tecnologia do Tocantins (IFTO, Brazil).
 *
 *     This file is part of CloudSim Plus.
 *
 *     CloudSim Plus is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CloudSim Plus is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CloudSim Plus. If not, see <http://www.gnu.org/licenses/>.
 */
package org.cloudsimplus.examples.dynamic;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.Simulation;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.util.Log;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows how to keep the simulation running, even
 * if there is no event to be processed anymore.
 * It calls the {@link Simulation#terminateAt(double)} to define
 * the time when the simulation must be terminated.
 *
 * <p>The example is useful when you want to run a simulation
 * for a specific amount of time.
 * For instance, if you want to run a simulation for 24 hours,
 * you just need to call {@code simulation.terminateAt(60*60*24)} (realize the value is in seconds).</p>
 *
 * <p>The example creates 4 Cloudlets and 2 VMs statically.
 * These Cloudlets will take 10 seconds to finish, but
 * the simulation is set to terminate
 * only after that ({@link #TIME_TO_TERMINATE_SIMULATION}).
 * This way, the simulation keeps waiting for dynamically
 * arrived events (such as creation of VMs and Cloudlets in runtime).
 * </p>
 *
 * <p>The example uses the CloudSim Plus {@link EventListener} feature
 * to enable monitoring the simulation and dynamically create Cloudlets and VMs at runtime.
 * It relies on
 * <a href="https://docs.oracle.com/javase/tutorial/java/javaOO/methodreferences.html">Java 8 Method References</a>
 * to set a method to be called for {@link Simulation#addOnClockTickListener(EventListener) onClockTick events}.
 * It enables getting notifications when the simulation clock advances, then creating and submitting new cloudlets.
 * </p>
 *
 * <p>Since the simulation was set to keep waiting for new events
 * until a defined time, the simulation clock will be updated
 * even if no event arrives, to simulate time passing.
 * Check the {@link Simulation#terminateAt(double)} for details.
 * </p>
 *
 * <p>At the time defined in {@link #TIME_TO_CREATE_NEW_CLOUDLET},
 * 1 Cloudlet and 1 VM is created, so the results will show 5 Cloudlets.
 * The simulation will just end at the specified time.
 * </p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 2.3.0
 */
public class KeepSimulationRunningExample {
    /**
     * @see #createDynamicCloudletAndVm(EventInfo)
     */
    private static final int TIME_TO_CREATE_NEW_CLOUDLET = 15;

    /**
     * @see Simulation#terminateAt(double)
     */
    private static final int TIME_TO_TERMINATE_SIMULATION = TIME_TO_CREATE_NEW_CLOUDLET*2;

    /**
     * @see Datacenter#getSchedulingInterval()
     */
    private static final int SCHEDULING_INTERVAL = 1;

    private static final int HOSTS = 1;
    private static final int HOST_PES = 8;

    private static final int VMS = 2;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = 4;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10000;

    private final CloudSim simulation;
    private DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new KeepSimulationRunningExample();
    }

    public KeepSimulationRunningExample() {
        simulation = new CloudSim();
        simulation.terminateAt(TIME_TO_TERMINATE_SIMULATION);
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.addOnClockTickListener(this::createDynamicCloudletAndVm);

        simulation.start();

        final List<Cloudlet> finishedCloudlets = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(finishedCloudlets).build();
    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private Datacenter createDatacenter() {
        final List<Host> hostList = new ArrayList<>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            Host host = createHost();
            hostList.add(host);
        }

        final Datacenter dc = new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        return dc;
    }

    private Host createHost() {
        List<Pe> peList = new ArrayList<>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000, new PeProvisionerSimple()));
        }

        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes
        Host host = new HostSimple(ram, bw, storage, peList);
        host
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms() {
        final List<Vm> list = new ArrayList<>(VMS);
        for (int i = 0; i < VMS; i++) {
            list.add(createVm());
        }

        return list;
    }

    private Vm createVm() {
        return new VmSimple(1000, VM_PES)
            .setRam(512).setBw(1000).setSize(10000)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    /**
     * Creates a list of Cloudlets.
     */
    private List<Cloudlet> createCloudlets() {
        final List<Cloudlet> list = new ArrayList<>(CLOUDLETS);
        for (int i = 0; i < CLOUDLETS; i++) {
            list.add(createCloudlet());
        }

        return list;
    }

    private Cloudlet createCloudlet() {
        return new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES)
            .setFileSize(1024)
            .setOutputSize(1024)
            .setUtilizationModel(new UtilizationModelFull());
    }

    /**
     * Simulates the dynamic arrival of a Cloudlet and a VM during simulation runtime.
     * @param evt
     */
    private void createDynamicCloudletAndVm(EventInfo evt) {
        if((int)evt.getTime() == TIME_TO_CREATE_NEW_CLOUDLET){
            Log.printFormattedLine("\n# Dynamically creating 1 Cloudlet and 1 VM at time %.2f\n", evt.getTime());
            Vm vm = createVm();
            vmList.add(vm);
            Cloudlet cloudlet = createCloudlet();
            cloudletList.add(cloudlet);

            broker0.submitVm(vm);
            broker0.submitCloudlet(cloudlet);
        }
    }
}
