package client;

import util.Packet;
import util.Timer;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ReliableClientProtocol {
    static int lastack = 0, window = 100;
    static List<DatagramPacket> packets = new ArrayList<>();

    static InetSocketAddress routerAddr = new InetSocketAddress("192.168.0.12", 3000);

    public static int connection(DatagramSocket socket, InetSocketAddress serverAddr) throws IOException {
        byte[] pck2 = new byte[1024];


        Packet p = new Packet.Builder()
                .setType(2)
                .setSequenceNumber(1L)
                .setPortNumber(serverAddr.getPort())
                .setPeerAddress(serverAddr.getAddress())
                .setPayload("1".getBytes())
                .create();

        byte[] pck = p.toBytes();
        DatagramPacket packet
                = new DatagramPacket(pck, pck.length, routerAddr.getAddress(), routerAddr.getPort());
        socket.send(packet);



        DatagramPacket synack = new DatagramPacket(pck2, pck2.length);
        socket.setSoTimeout(20);

        while (true) {
            try {
                socket.receive(synack);
                Packet synackpacket = Packet.fromBytes(synack.getData());
                if (synackpacket.getType() == 3 && synackpacket.getSequenceNumber() == 1) {
                    break;
                }
            } catch (SocketTimeoutException s) {
                socket.send(packet);
                continue;
            }
        }


        p = new Packet.Builder()
                .setType(1)
                .setSequenceNumber(p.getSequenceNumber())
                .setPortNumber(p.getPeerPort())
                .setPeerAddress(p.getPeerAddress())
                .setPayload("11".getBytes())
                .create();
        pck = p.toBytes();
        packet = new DatagramPacket(pck, pck.length, packet.getAddress(), packet.getPort());
        socket.send(packet);

        String serverport = new String(Packet.fromBytes(synack.getData()).getPayload(), StandardCharsets.UTF_8);
        int sport = Integer.parseInt(serverport.trim());
        return sport;


    }

//    public static void main(String[] args) {
//        String data = "";
//        for (int i = 0; i < 5000; i++)
//            data += "a";
//        try {
//            DatagramSocket socket = new DatagramSocket();
//
//            InetSocketAddress serverAddress = new InetSocketAddress("192.168.0.12", 8000);
//            int port = ReliableClientProtocol.connection(socket, serverAddress);
//            ReliableClientProtocol.requestresponse(socket, new InetSocketAddress("192.168.0.12", port), data);
////            client.ReliableClientProtocol.requestresponse(socket, new InetSocketAddress("192.168.0.12", port), data);
//
//        } catch (IOException s) {
//            s.printStackTrace();
//        }
//
//
//    }

    public static String requestresponse(DatagramSocket socket, InetSocketAddress serverAddr, String data) throws IOException {
        byte[] datab = data.getBytes();

        final int sizeMB = 1013;
        List<byte[]> chunks = IntStream.iterate(0, i -> i + sizeMB)
                .limit((datab.length + sizeMB - 1) / sizeMB)
                .mapToObj(i -> Arrays.copyOfRange(datab, i, Math.min(i + sizeMB, datab.length)))
                .collect(Collectors.toList());
        //fist packet having size
        int nofpackets = chunks.size();
        Packet p = new Packet.Builder()
                .setType(0)
                .setSequenceNumber(1L)
                .setPortNumber(serverAddr.getPort())
                .setPeerAddress(serverAddr.getAddress())
                .setPayload(Integer.toString(nofpackets + 1).getBytes())
                .create();
        DatagramPacket size = new DatagramPacket(p.toBytes(), p.toBytes().length, routerAddr.getAddress(), routerAddr.getPort());
        socket.send(size);

        //first packet ack
        byte[] pck2 = new byte[1024];
        DatagramPacket ack = new DatagramPacket(pck2, pck2.length);
        socket.setSoTimeout(50);

        while (true) {
            try {
                socket.receive(ack);
                Packet ackpacket = Packet.fromBytes(ack.getData());
                if (ackpacket.getType() == 1 && ackpacket.getSequenceNumber() == 1) {
                    break;
                }

                //send ack if we get some data instead of size ack
                if (ackpacket.getType() == 0) {
                    p = new Packet.Builder()
                            .setType(1)
                            .setSequenceNumber(ackpacket.getSequenceNumber())
                            .setPortNumber(ackpacket.getPeerPort())
                            .setPeerAddress(ackpacket.getPeerAddress())
                            .setPayload("".getBytes())
                            .create();
                    DatagramPacket wack = new DatagramPacket(p.toBytes(), p.toBytes().length, routerAddr.getAddress(), routerAddr.getPort());
                    socket.send(wack);
                }

            } catch (SocketTimeoutException s) {
                socket.send(size);
                continue;
            }
        }
        socket.setSoTimeout(0);

        sendreq(nofpackets, serverAddr, socket, chunks);

        //call getresponse
        String res = response(socket);


        return res;

    }


    public static void sendreq(int nofpackets, InetSocketAddress serverAddr, DatagramSocket socket, List<byte[]> chunks) throws IOException {

        lastack = 0;
        window = 100;
        packets = new ArrayList<>();

        for (int i = 2; i <= nofpackets + 1; i++) {
            Packet cp = new Packet.Builder()
                    .setType(0)
                    .setSequenceNumber(i)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(chunks.get(i - 2))
                    .create();
            DatagramPacket dcp = new DatagramPacket(cp.toBytes(), cp.toBytes().length, routerAddr.getAddress(), routerAddr.getPort());
            packets.add(i - 2, dcp);

        }

        int tobesent = Math.min(nofpackets, window);
        for (int i = 2; i <= tobesent + 1; i++) {
            socket.send(packets.get(i - 2));
            //start timer thread
            Thread t = new Thread(new Timer(i, socket, 0));
            t.start();
        }


        while (lastack < nofpackets + 1) {
            byte[] pck = new byte[1024];
            DatagramPacket ack = new DatagramPacket(pck, pck.length);
            socket.receive(ack);
            Packet ackpacket = Packet.fromBytes(ack.getData());

            if (ackpacket.getType() == 1 && ackpacket.getSequenceNumber() > lastack && ackpacket.getSequenceNumber() <= window + 1) {
                int newacs = (int) ackpacket.getSequenceNumber() - lastack + 1;

                if (packets.size() > window) {
                    for (int i = window; i < window + newacs; i++) {
                        if (packets.size() > i) {
                            socket.send(packets.get(i));
                            Thread t = new Thread(new Timer(i + 2, socket, 0));
                            t.start();
                            //start thread
                        } else
                            break;
                    }
                }

                window = window + newacs;
                lastack = (int) ackpacket.getSequenceNumber();
            }

            if (ackpacket.getType() == 4 && ackpacket.getSequenceNumber() > lastack && ackpacket.getSequenceNumber() <= window + 1) {
                socket.send(packets.get((int) ackpacket.getSequenceNumber() - 2));
                Thread t = new Thread(new Timer((int) ackpacket.getSequenceNumber(), socket, 0));
                t.start();
                //thread
            }

        }


    }

    public synchronized static void isAcked(int seq, DatagramSocket socket, int retries) throws IOException {
        if (seq <= lastack || retries >= 10) {
//            System.out.println("got acks for packet " + seq);
        } else {
            //resend
            //create new thread
            socket.send(packets.get(seq - 2));
            Thread t = new Thread(new Timer(seq, socket, retries + 1));
            t.start();

        }
    }

    static String response(DatagramSocket socket) {
        String response="";
        while (true) {
            DatagramPacket req = new DatagramPacket(new byte[1024], 1024);
            try {
                socket.receive(req);
                Packet p = Packet.fromBytes(req.getData());
                if (p.getType() == 6) {
                    //close the connection
                    break;
                }
                if (p.getSequenceNumber() == 1 && p.getType()==0) {
                    String total = new String(p.getPayload());
                    Packet ack = new Packet(1, 1L, p.getPeerAddress(), p.getPeerPort(), new byte[0]);
                    DatagramPacket ackP = new DatagramPacket(ack.toBytes(), ack.toBytes().length, req.getAddress(), req.getPort());
                    socket.send(ackP);
                    response = getRequestFromPacket(socket, Integer.parseInt(total.trim()));
                    break;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return response;

    }

    public static String getRequestFromPacket(DatagramSocket socket,int totalPackets){
        int expseqNum = 2;
        int maxWindow = 100;
        int i;
        HashMap<Integer, byte[]> data = new HashMap<>();
        while(expseqNum <= totalPackets)
        {
            DatagramPacket req = new DatagramPacket(new byte[1024],1024);
            try {
                socket.receive(req);
                Packet p = Packet.fromBytes(req.getData());
                int pSeq = (int) p.getSequenceNumber();
                InetAddress clientAddress = p.getPeerAddress();
                int clientPort = p.getPeerPort();
                InetAddress rAddress = req.getAddress();
                int rPort = req.getPort();
                if(pSeq <= maxWindow)
                {
                    if(pSeq == expseqNum)
                    {
                        data.put(expseqNum,p.getPayload());

                        for(i=expseqNum;i<=totalPackets;i++)
                        {
                            if(data.containsKey(i))
                            {
                                expseqNum++;
                                maxWindow++;

                            }else{
                                Packet ack = new Packet(1,i-1,clientAddress,clientPort,new byte[0]);
                                DatagramPacket ackP = new DatagramPacket(ack.toBytes(),ack.toBytes().length,rAddress,rPort);
                                socket.send(ackP);
                                break;
                            }

                        }
                    }
                    if(pSeq > expseqNum)
                    {
                        data.put(pSeq,p.getPayload());
                        for(i=expseqNum;i<pSeq;i++)
                        {
                            if(!data.containsKey(i)) {
                                Packet nack = new Packet(4, i, clientAddress, clientPort, new byte[0]);
                                DatagramPacket nackP = new DatagramPacket(nack.toBytes(), nack.toBytes().length, rAddress, rPort);
                                socket.send(nackP);
                            }
                        }
                    }
                    if(pSeq < expseqNum)
                    {
                        Packet ack = new Packet(1,pSeq,clientAddress,clientPort,new byte[0]);
                        DatagramPacket ackP = new DatagramPacket(ack.toBytes(),ack.toBytes().length,rAddress,rPort);
                        socket.send(ackP);
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
        String request="";
        int j ,k=0;
        byte[] reqArr = new byte[(totalPackets-1) * 1013];
        for(i=2;i<=totalPackets;i++)
        {
            byte[] tmp = data.get(i);
            for(j=0;j<tmp.length;j++)
            {
                reqArr[k] = tmp[j];
                k++;
            }
        }
        request = new String(reqArr);
        return request.trim();
    }

}
