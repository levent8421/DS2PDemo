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
import com.berrontech.weight.hardware.ds2p.cluster.weight.DefaultCountingStrategy;
import com.berrontech.weight.hardware.ds2p.cluster.weight.WeightDeviceCluster;
import com.berrontech.weight.hardware.ds2p.cluster.weight.WeightDeviceClusterSkuInfo;
import com.berrontech.weight.hardware.ds2p.conn.serial.SerialPortConnection;
import com.berrontech.weight.hardware.ds2p.device.esl.WeightEslDevice;
import com.berrontech.weight.hardware.ds2p.device.esl.WeightEslDeviceParam;
import com.berrontech.weight.hardware.ds2p.device.weight.WeightSensorDevice;
import com.berrontech.weight.hardware.ds2p.device.weight.WeightSensorDeviceParams;
import com.berrontech.weight.hardware.ds2p.group.general.GeneralDeviceGroup;
import com.berrontech.weight.hardware.ds2p.identification.ClusterUrl;
import com.berrontech.weight.hardware.ds2p.identification.ConnectionUrl;
import com.berrontech.weight.hardware.ds2p.identification.DeviceUrl;
import com.berrontech.weight.hardware.ds2p.identification.IdentificationUrl;
import com.berrontech.weight.hardware.ds2p.identification.IdentificationUrlBuilder;
import com.berrontech.weight.hardware.ds2p.impl.DefaultDeviceManager;
import com.berrontech.weight.hardware.ds2p.proto.DataChannel;
import com.berrontech.weight.hardware.ds2p.proto.Ds2pProtocolMeta;
import com.berrontech.weight.hardware.ds2p.proto.impl.DefaultDataChannel;

import java.math.BigDecimal;
import java.net.URLEncoder;
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

    private DataChannel channel;
    private DeviceManager deviceManager = new DefaultDeviceManager();

    private void start() throws Exception {
        // 构建数据通道（与硬件链接的通道）
        String serialPort = serialPorts.get(spSerialPort.getSelectedItemPosition());
        int baudRate = 115200;
        try {
            baudRate = Integer.parseInt(etBaudRate.getText().toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        Ds2pSerialPortWrapper portWrapper = new Ds2pSerialPortWrapper(serialPort, baudRate);
        SerialPortConnection connection = new SerialPortConnection(portWrapper);
        channel = new DefaultDataChannel(connection);
        channel.init();
        // 构建设备组，一个设备组对应底层一套线程，维护一个物理链接下的所有设备
        ConnectionUrl connectionUrl = IdentificationUrlBuilder.parseConnectionUrl("ds2p:uart://" + URLEncoder.encode(serialPort, "utf8") + ":115200/conn");
        GeneralDeviceGroup group = new GeneralDeviceGroup(connectionUrl, channel);

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
        for (int i = 0; i < deviceCount; i++) {
            int addr = addrBegin + i;
            WeightDeviceCluster cluster = buildCluster(channel, serialPort, baudRate, addr, esl);
            group.addCluster(cluster);
        }

        // 将设备组注册到设备管理器中
        deviceManager.addGroup(group);
        // 启动设备管理器
        deviceManager.startSchedule();

        btnStop.setEnabled(true);
        btnStart.setEnabled(false);
    }

    private WeightDeviceCluster buildCluster(DataChannel channel, String serialPort, int baudRate, int addr, boolean esl) {
        // 传感器
        WeightSensorDevice sensor = new WeightSensorDevice(channel);
        WeightSensorDeviceParams sensorParams = sensor.getParams();
        sensorParams.setProtocolVersion(Ds2pProtocolMeta.Versions.V21);
        sensorParams.setAddress(addr);
        // ds2p:uart://ttyS1:115200/addr/1-n
        DeviceUrl sensorUrl = new IdentificationUrlBuilder().withSubProtocol(IdentificationUrl.PROTOCOL_UART)
                .withConnection(serialPort)
                .withConnectionParams(Collections.singletonList(baudRate))
                .addPath(String.valueOf(addr))
                .buildDeviceUrl();
        sensorParams.setId(sensorUrl);
        // 电子标签
        WeightEslDevice eslDevice = null;
        if (esl) {
            int eslAddr = addr + 100;
            DeviceUrl eslUrl = new IdentificationUrlBuilder().withSubProtocol(IdentificationUrl.PROTOCOL_UART)
                    .withConnection(serialPort)
                    .withConnectionParams(Collections.singletonList(baudRate))
                    .addPath(String.valueOf(eslAddr))
                    .buildDeviceUrl();
            eslDevice = new WeightEslDevice(channel);
            WeightEslDeviceParam eslParams = eslDevice.getParams();
            eslParams.setAddress(eslAddr);
            eslParams.setProtocolVersion(Ds2pProtocolMeta.Versions.V21);
            eslParams.setId(eslUrl);
        }
        // 库位
        // 库位URI:    ds2p:uart//ttyS1:115200/cluster/S-addr
        ClusterUrl clusterUrl = new IdentificationUrlBuilder()
                .withSubProtocol(IdentificationUrl.PROTOCOL_UART)
                .withConnection(serialPort)
                .withConnectionParams(Collections.singletonList(String.valueOf(baudRate)))
                .addPath("S-" + addr)
                .buildClusterUrl();

        WeightDeviceCluster cluster = new WeightDeviceCluster(clusterUrl, Collections.singletonList(sensor), eslDevice, null);
        // 状态监听， 重量变化、数量变化等的回调
        cluster.setStateListener(new LogClusterStateListener());
        // 计数策略， 可以把物品单重、允差等设置到这里
        // 此处单重0.1kg 允差0.01kg 最大允差0.05kg
        DefaultCountingStrategy countingStrategy = new DefaultCountingStrategy();
        countingStrategy.setTolerance(BigDecimal.valueOf(0.01));
        countingStrategy.setApw(BigDecimal.valueOf(0.1));
        countingStrategy.setMaxError(BigDecimal.valueOf(0.05));
        cluster.setCountingStrategy(countingStrategy);

        // 库位号，物料名称、物料号等
        cluster.setClusterName("S-" + addr);
        WeightDeviceClusterSkuInfo skuInfo = new WeightDeviceClusterSkuInfo();
        skuInfo.setSkuNo("123123");
        skuInfo.setSkuName("物料名称");
        cluster.setSkuInfo(skuInfo);
        return cluster;
    }

    private void stop() throws Ds2pException {
        // 尝试停止服务,timeout表示底层将等待1000ms设备主动退出现场，否则将强制杀死
        deviceManager.shutdown(1000);
        channel.deInit();

        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
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
}