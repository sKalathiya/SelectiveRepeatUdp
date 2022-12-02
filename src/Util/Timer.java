package Util;

import Server.ServerTcpProtocl;

import java.io.IOException;
import java.net.DatagramSocket;

import static Server.ServerTcpProtocl.clientHandler.isAcked;

public class Timer implements Runnable{
    int seq;
    int retries ;
    DatagramSocket socket;
    public Timer(int seq, DatagramSocket socket,int retries){
        this.seq=seq;
        this.socket = socket;
        this.retries = retries;
    }

    @Override
    public void run() {
        //waiting for ACK
        try {
            Thread.sleep(30);
            isAcked(seq, socket,retries);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

    }
}