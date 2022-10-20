package com.levent8421.ds2pdemo;

import com.berrontech.weight.hardware.ds2p.cluster.weight.WeightClusterStateListener;
import com.berrontech.weight.hardware.ds2p.cluster.weight.WeightDeviceCluster;
import com.berrontech.weight.hardware.ds2p.cluster.weight.WeightDeviceClusterState;
import com.berrontech.weight.hardware.ds2p.device.esl.WeightEslDevice;
import com.berrontech.weight.hardware.ds2p.device.weight.WeightSensorDevice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Create By Levent8421
 * Create Time: 2022/6/1 16:26
 * Class Name: LogClusterStateListener
 * Author: Levent8421
 * Description:
 * 仅作日志输出的库位状态监听
 *
 * @author Levent8421
 */
public class LogClusterStateListener implements WeightClusterStateListener {
    private static final Logger log = LoggerFactory.getLogger(LogClusterStateListener.class);

    @Override
    public void onWeightValueChange(WeightDeviceCluster cluster) throws Exception {
        String uri = cluster.getIdentification().asIdentificationKey();
        BigDecimal weight = cluster.getClusterState().getWeight();
        log.debug("Weight change:[{}]:{}", uri, weight);
    }

    @Override
    public void onWeightCountChange(WeightDeviceCluster cluster) throws Exception {
        String uri = cluster.getIdentification().asIdentificationKey();
        WeightDeviceClusterState clusterState = cluster.getClusterState();
        int count = clusterState.getCount();
        boolean countingInAccuracy = clusterState.isCountingAccuracy();
        log.debug("Count change:[{}]:{}/{}", uri, count, countingInAccuracy);
    }

    @Override
    public void onSensorConnectionStateChange(WeightDeviceCluster cluster, WeightSensorDevice sensorDevice) throws Exception {
        String name = cluster.getName();
        String sensorUrl = sensorDevice.getParams().getId().asIdentificationKey();
        boolean online = sensorDevice.isOnline();
        log.debug("Sensor connChanged: [{}]/[{}]/{}", name, sensorUrl, online);
    }

    @Override
    public void onEslConnectionStateChange(WeightDeviceCluster cluster, WeightEslDevice eslDevice) throws Exception {
        String name = cluster.getName();
        String sensorUrl = eslDevice.getParams().getId().asIdentificationKey();
        boolean online = eslDevice.isOnline();
        log.debug("ESL connChanged: [{}]/[{}]/{}", name, sensorUrl, online);
    }

    @Override
    public void onMasterEslKeyLongPress(WeightDeviceCluster cluster, WeightEslDevice eslDevice) throws Exception {

    }

    @Override
    public void onZombieEslKeyLongPress(WeightDeviceCluster cluster, WeightEslDevice eslDevice) throws Exception {

    }

    @Override
    public void onEnableChange(WeightDeviceCluster cluster) throws Exception {

    }
}
