package com.levent8421.ds2pdemo;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SimpleAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.berrontech.weight.hardware.ds2p.DeviceManager;
import com.berrontech.weight.hardware.ds2p.Ds2pException;
import com.berrontech.weight.hardware.ds2p.building.SimpleUrlDeviceFactory;
import com.berrontech.weight.hardware.ds2p.cluster.DeviceCluster;
import com.berrontech.weight.hardware.ds2p.cluster.weight.WeightDeviceCluster;
import com.berrontech.weight.hardware.ds2p.device.Ds2pDeviceException;
import com.berrontech.weight.hardware.ds2p.group.DeviceGroup;
import com.berrontech.weight.hardware.ds2p.identification.ClusterUrl;
import com.berrontech.weight.hardware.ds2p.identification.ConnectionUrl;
import com.berrontech.weight.hardware.ds2p.identification.DeviceUrl;
import com.berrontech.weight.hardware.ds2p.identification.IdentificationUrl;
import com.berrontech.weight.hardware.ds2p.identification.IdentificationUrlBuilder;
import com.berrontech.weight.hardware.ds2p.impl.DefaultDeviceManager;
import com.berrontech.weight.hardware.ds2p.proto.DataChannel;
import com.google.common.collect.Lists;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import android_serialport_api.SerialUtils;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private Button btnStart;
    private Button btnStop;
    private Spinner spSerialPort;
    private EditText etAddrBegin;
    private EditText etDeviceCount;
    private EditText etBaudRate;
    private CheckBox cbEsl;
    private final List<String> serialPorts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
    }

    private void initViews() {
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        btnStop.setEnabled(false);
        btnStart.setEnabled(true);
        spSerialPort = findViewById(R.id.sp_serial_port);
        etAddrBegin = findViewById(R.id.et_addr_begin);
        etDeviceCount = findViewById(R.id.et_device_count);
        etBaudRate = findViewById(R.id.et_baud_rate);
        cbEsl = findViewById(R.id.cb_esl);
        loadSerialPorts();
        btnStart.setOnClickListener(this);
        btnStop.setOnClickListener(this);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        try {
            switch (view.getId()) {
                case R.id.btn_start:
                    start();
                    break;
                case R.id.btn_stop:
                    stop();
                    break;
                default:
                    // Do nothing
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //// -----------------------??????????????????----------------------------------------
    private final List<DataChannel> channels = Lists.newArrayList();
    private final DeviceManager deviceManager = new DefaultDeviceManager();

    /**
     * ??????Linux???????????????????????????????????????????????????
     */
    private void loadSerialPorts() {
        try {
            List<String> ports = SerialUtils.scanLinuxPorts();
            serialPorts.clear();
            serialPorts.addAll(ports);
            List<Map<String, String>> data = new ArrayList<>();
            for (String port : ports) {
                data.add(Collections.singletonMap("port", port));
            }
            SimpleAdapter adapter = new SimpleAdapter(this, data, android.R.layout.simple_expandable_list_item_1, new String[]{"port"}, new int[]{android.R.id.text1});
            spSerialPort.setAdapter(adapter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ??????????????????????????????????????? ??????URL
     * ds2p:uart//%2Fdev%2FttyS0:115200/conn
     * ds2p:tcp//192.168.2.1:20108/conn
     *
     * @return ConnectionUrl
     */
    private ConnectionUrl getConnectionUrl() {
        int index = spSerialPort.getSelectedItemPosition();
        String portName = serialPorts.get(index);
        String baudRate = etBaudRate.getText().toString();
        // ds2p:uart://ttyS0:115200/conn
        return new IdentificationUrlBuilder()
                .withPrimaryProtocol(IdentificationUrl.PROTOCOL_DS2P)
                .withSubProtocol(IdentificationUrl.PROTOCOL_UART)
                .withConnection(portName)
                .withConnectionParams(List.of(baudRate))
                .buildConnectionUrl();
        // ????????????????????????
        // ??????????????????URL????????????????????????????????????
        /*
        String urlStr = "ds2p:uart://%2Fdev%2FttyS0:115200/conn";
        ConnectionUrl url = IdentificationUrlBuilder.parseConnectionUrl(urlStr);
        */
    }

    /**
     * ??????????????????
     *
     * @param startAddr ????????????
     * @param count     ????????????
     * @param esl       ????????????????????????
     * @return ?????????
     * @throws Ds2pDeviceException error
     */
    private List<DeviceGroup> build(int startAddr, int count, boolean esl) throws Ds2pDeviceException {
        ConnectionUrl connectionUrl = getConnectionUrl();
        // ???????????????????????????????????????????????????????????????
        SimpleUrlDeviceFactory factory = new SimpleUrlDeviceFactory((deviceName, baudRate) -> {
            try {
                return new Ds2pSerialPortWrapper(deviceName, baudRate);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(ExceptionUtils.getMessage(e), e);
            }
        });
        // ????????????
        for (int i = 0; i < count; i++) {
            buildWeightCluster(connectionUrl, factory, startAddr + i, esl);
        }
        // ???????????????????????????????????????
        return factory.getGroups();
    }

    /**
     * ??????????????????
     *
     * @param connectionUrl ??????URL
     * @param factory       ??????
     * @param address       ??????
     * @param esl           ????????????,true??????????????????(address+100)?????????????????????
     * @throws Ds2pDeviceException error
     */
    private void buildWeightCluster(ConnectionUrl connectionUrl, SimpleUrlDeviceFactory factory, int address, boolean esl) throws Ds2pDeviceException {
        List<DeviceUrl> sensors = Lists.newArrayList();
        // ???????????????????????????
        // ds2p:uart://ttyS0:115200/addr/1
        DeviceUrl sensorUrl = new IdentificationUrlBuilder()
                .copyConnection(connectionUrl)
                .addPath(String.valueOf(address))
                .buildDeviceUrl();
        sensors.add(sensorUrl);
        DeviceUrl masterEsl;
        if (esl) {
            // ????????????????????????
            masterEsl = new IdentificationUrlBuilder()
                    .copyConnection(connectionUrl)
                    .addPath(String.valueOf(address + 100))
                    .buildDeviceUrl();
        } else {
            // ?????????????????????
            masterEsl = null;
        }
        // ??????
        // ds2p:uart://ttyS0:115200/cluster/S1-1
        ClusterUrl clusterUrl = new IdentificationUrlBuilder()
                .copyConnection(connectionUrl)
                .addPath("S1-" + address) // ?????????????????????????????????
                .buildClusterUrl();
        factory.buildWeightCluster(clusterUrl, sensors, masterEsl, Collections.emptyList());
    }

    /**
     * ??????????????????
     *
     * @throws Exception error
     */
    private void start() throws Exception {
        // ????????????
        int addrBegin = 1;
        int deviceCount = 10;
        try {
            addrBegin = Integer.parseInt(etAddrBegin.getText().toString());
            deviceCount = Integer.parseInt(etDeviceCount.getText().toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        boolean esl = cbEsl.isChecked();
        List<DeviceGroup> groups = build(addrBegin, deviceCount, esl);
        channels.clear();
        for (DeviceGroup group : groups) {
            // ???????????????????????????????????????
            deviceManager.addGroup(group);
            DataChannel channel = group.getChannel();
            // ???????????????????????????????????????????????????????????????deviceManager.startSchedule()??????????????????
            channel.init();
            // ??????????????????????????????????????????
            channels.add(channel);
        }
        // ?????????????????????
        deviceManager.startSchedule();
        // ?????????????????????(?????????????????????????????????)
        List<DeviceGroup> groupList = deviceManager.getGroups();
        for (DeviceGroup group : groupList) {
            for (DeviceCluster cluster : group.getClusters()) {
                WeightDeviceCluster weightDeviceCluster = (WeightDeviceCluster) cluster;
                // ???????????????
                weightDeviceCluster.setStateListener(new LogClusterStateListener());
            }
        }
        btnStop.setEnabled(true);
        btnStart.setEnabled(false);
    }

    /**
     * ??????????????????
     *
     * @throws Ds2pException exception
     */
    private void stop() throws Ds2pException {
        // ??????????????????,timeout?????????????????????1000ms????????????????????????????????????????????????
        deviceManager.shutdown(1000);
        // ??????????????????
        for (DataChannel channel : channels) {
            channel.deInit();
        }

        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }

    /// -----------------------??????????????????----------------------------------------
}