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
import com.berrontech.weight.hardware.ds2p.building.Ds2pBuildingException;
import com.berrontech.weight.hardware.ds2p.building.SimpleUrlDeviceFactory;
import com.berrontech.weight.hardware.ds2p.cluster.DeviceCluster;
import com.berrontech.weight.hardware.ds2p.cluster.weight.WeightDeviceCluster;
import com.berrontech.weight.hardware.ds2p.cluster.weight.WeightDeviceClusterConfig;
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

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

    //// -----------------------示例代码开始----------------------------------------
    private final List<DataChannel> channels = Lists.newArrayList();
    private final DeviceManager deviceManager = new DefaultDeviceManager();

    /**
     * 扫描Linux系统注册的所有串口，并显示到界面上
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
     * 使用界面选取的链接信息创建 链接URL
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
        /*return new IdentificationUrlBuilder()
                .withPrimaryProtocol(IdentificationUrl.PROTOCOL_DS2P)
                .withSubProtocol(IdentificationUrl.PROTOCOL_TCP)
                .withConnection("192.168.1.1")
                .withConnectionParams(List.of(10010))
                .buildConnectionUrl();*/

        // 另外一种构建方法
        // 以下所有构建URL的操作都可以使用两种方法
        /*
        String urlStr = "ds2p:uart://%2Fdev%2FttyS0:115200/conn";
        ConnectionUrl url = IdentificationUrlBuilder.parseConnectionUrl(urlStr);
        */
    }

    /**
     * 构建驱动结构
     *
     * @param startAddr 开始地址
     * @param count     设备数量
     * @param esl       是否存在电子标签
     * @return 设备组
     * @throws Ds2pDeviceException error
     */
    private List<DeviceGroup> build(int startAddr, int count, boolean esl) throws Ds2pDeviceException {
        ConnectionUrl connectionUrl = getConnectionUrl();
        // 创建一个工厂对象，并传入创建串口链接的策略
        SimpleUrlDeviceFactory factory = new SimpleUrlDeviceFactory((deviceName, baudRate) -> {
            try {
                return new Ds2pSerialPortWrapper(deviceName, baudRate);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(ExceptionUtils.getMessage(e), e);
            }
        });
        // 创建库位
        for (int i = 0; i < count; i++) {
            buildWeightCluster(connectionUrl, factory, startAddr + i, esl);
        }
        // 从工厂中获取已创建的设备组
        List<DeviceGroup> groups = factory.getGroups();
        WeightDeviceCluster cluster = (WeightDeviceCluster) groups.get(0).getClusters().get(0);

        String configFilePath = "/sdcard/weight_cluster.config";
        cluster.setConfig(new WeightDeviceClusterConfig() {
            @Override
            public void load() {
                super.load();
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    FileInputStream in = new FileInputStream(configFilePath);
                    try (out; in) {
                        int len;
                        byte[] buf = new byte[512];
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                    String factorStr = out.toString();
                    BigDecimal factor = new BigDecimal(factorStr);
                    this.setWeightCorrectionFactor(factor);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void write() {
                super.write();
                BigDecimal factor = this.getWeightCorrectionFactor();
                if (factor == null) {
                    factor = BigDecimal.ONE;
                }
                try {
                    String s = factor.toString();
                    try (FileOutputStream out = new FileOutputStream(configFilePath)) {
                        out.write(s.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        return groups;
    }

    public void doWeightCorrect() throws Ds2pDeviceException {
        BigDecimal targetWeight = BigDecimal.valueOf(100);
        WeightDeviceCluster cluster = (WeightDeviceCluster) deviceManager.getGroups().get(0).getClusters().get(0);
        cluster.updateWeightCorrectionFactor(targetWeight);
    }

    /**
     * 创建一个库位
     *
     * @param connectionUrl 链接URL
     * @param factory       工厂
     * @param address       地址
     * @param esl           电子标签,true时创建地址为(address+100)的电子标签设备
     * @throws Ds2pDeviceException error
     */
    private void buildWeightCluster(ConnectionUrl connectionUrl, SimpleUrlDeviceFactory factory, int address, boolean esl) throws Ds2pDeviceException {
        List<DeviceUrl> sensors = Lists.newArrayList();
        // 此处创建一个传感器
        // ds2p:uart://ttyS0:115200/addr/1
        DeviceUrl sensorUrl = new IdentificationUrlBuilder()
                .copyConnection(connectionUrl)
                .addPath(String.valueOf(address))
                .buildDeviceUrl();
        sensors.add(sensorUrl);
        DeviceUrl masterEsl;
        if (esl) {
            // 需要创建电子标签
            masterEsl = new IdentificationUrlBuilder()
                    .copyConnection(connectionUrl)
                    .addPath(String.valueOf(address + 100))
                    .buildDeviceUrl();
        } else {
            // 不需要电子标签
            masterEsl = null;
        }
        // 库位
        // ds2p:uart://ttyS0:115200/cluster/S1-1
        ClusterUrl clusterUrl = new IdentificationUrlBuilder()
                .copyConnection(connectionUrl)
                .addPath("S1-" + address) // 与库位名一致，全局唯一
                .buildClusterUrl();
        try {
            factory.buildWeightCluster(clusterUrl, sensors, masterEsl, Collections.emptyList());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 启动重力服务
     *
     * @throws Exception error
     */
    private void start() throws Exception {
        // 构建设备
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
            // 将设备组注册到设备管理器中
            deviceManager.addGroup(group);
            DataChannel channel = group.getChannel();
            // 初始化数据通道，很重要！！！！！！，在调用deviceManager.startSchedule()前必须初始化
            channel.init();
            // 保存数据通道，方标停止时销毁
            channels.add(channel);
        }
        // 启动设备管理器
        deviceManager.startSchedule();
        // 监听库位的状态(重量、数量、离线在线等)
        List<DeviceGroup> groupList = deviceManager.getGroups();
        for (DeviceGroup group : groupList) {
            for (DeviceCluster cluster : group.getClusters()) {
                WeightDeviceCluster weightDeviceCluster = (WeightDeviceCluster) cluster;
                // 此处仅打印
                weightDeviceCluster.setStateListener(new LogClusterStateListener());
            }
        }
        btnStop.setEnabled(true);
        btnStart.setEnabled(false);
    }

    /**
     * 停止重力服务
     *
     * @throws Ds2pException exception
     */
    private void stop() throws Ds2pException {
        // 尝试停止服务,timeout表示底层将等待1000ms设备主动退出现场，否则将强制杀死
        deviceManager.shutdown(1000);
        // 销毁数据通道
        for (DataChannel channel : channels) {
            channel.deInit();
        }

        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }

    private void doZero() {
        ClusterUrl url;
        try {
            // 库位ID（建议在构建时就保存该URL，之后使用该URL与DS2P驱动通信，即拿到该URL就可以对对应的设备操作·），
            url = IdentificationUrlBuilder.parseClusterUrl("ds2p:uart://xxx:115200/cluster/S1-1-1");
        } catch (Ds2pBuildingException e) {
            e.printStackTrace();
            return;
        }
        WeightDeviceCluster cluster = (WeightDeviceCluster) deviceManager.findCluster(url);
        if (cluster == null) {
            return;
        }
        try {
            cluster.doZero();
        } catch (Ds2pDeviceException e) {
            e.printStackTrace();
        }
    }
    /// -----------------------示例代码结束----------------------------------------

    private void buildMultiSensors() throws Ds2pException {
        // 示例： 构建库位S-1 包含地址为1、2、3、4的四个传感器
        // Factory 不使用串口，因此serialPortFactory参数为Null
        SimpleUrlDeviceFactory factory = new SimpleUrlDeviceFactory(null);
        ConnectionUrl connectionUrl = getConnectionUrl();
        ClusterUrl clusterUrl = new IdentificationUrlBuilder()
                .copyConnection(connectionUrl)
                .addPath("S-1")
                .buildClusterUrl();
        List<DeviceUrl> sensors = Lists.newArrayList();
        for (int addr = 1; addr <= 4; addr++) {
            DeviceUrl deviceUrl = new IdentificationUrlBuilder()
                    .copyConnection(connectionUrl)
                    .addPath(String.valueOf(addr))
                    .buildDeviceUrl();
            sensors.add(deviceUrl);
        }
        factory.buildWeightCluster(clusterUrl, sensors, null, Collections.emptyList());
        // 后续流程与原来保持一致
        List<DeviceGroup> groups = factory.getGroups();
        for (DeviceGroup group : groups) {
            deviceManager.addGroup(group);
            DataChannel channel = group.getChannel();
            channel.init();
        }

    }

}