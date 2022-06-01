package com.levent8421.ds2pdemo;

import com.berrontech.weight.hardware.ds2p.conn.ConnectionException;
import com.berrontech.weight.hardware.ds2p.conn.serial.SerialWrapper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android_serialport_api.SerialPort;

/**
 * Create By Levent8421
 * Create Time: 2022/4/13 9:31
 * Class Name: Ds2pSerialPortWrapper
 * Author: Levent8421
 * Description:
 * 串口实现类
 *
 * @author Levent8421
 */
public class Ds2pSerialPortWrapper implements SerialWrapper {
    private InputStream serialIn;
    private OutputStream serialOut;
    private SerialPort serialPort;

    private final int baudRate;
    private final File file;

    public Ds2pSerialPortWrapper(String filename, int baudRate) throws FileNotFoundException {
        this.baudRate = baudRate;
        this.file = new File(filename);
        if (!file.exists()) {
            throw new FileNotFoundException(filename);
        }
    }

    @Override
    public void open() throws ConnectionException {
        try {
            this.serialPort = new SerialPort(file, baudRate, 0);
            serialIn = this.serialPort.getInputStream();
            serialOut = this.serialPort.getOutputStream();
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    @Override
    public void close() throws ConnectionException {
        if (serialPort != null) {
            serialPort.close();
        }
        serialPort = null;
        serialOut = null;
        serialIn = null;
    }

    @Override
    public int read(byte[] buffer, int offset, int len) throws ConnectionException {
        try {
            return serialIn.read(buffer, offset, len);
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    @Override
    public int write(byte[] buffer, int offset, int len) throws ConnectionException {
        try {
            serialOut.write(buffer, offset, len);
            return len;
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }
}
