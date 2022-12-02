package Server;

import Util.Packet;
import Util.TimeoutBlock;
import Util.Timer;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ServerTcpProtocl {
    private DatagramSocket connectionSocket;
    private static HashMap<InetAddress, DatagramSocket> socketMapping;
    private static String directory;
    private static boolean verbose;

    private static int lastackRec = 0 , windowRec = 100;
    private static List<DatagramPacket> packets = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        ServerTcpProtocl server = new ServerTcpProtocl(8000,".",true);

    }

    public ServerTcpProtocl(int port,String directory,boolean verbose) throws IOException {
        this.connectionSocket = new DatagramSocket(port);
        socketMapping = new HashMap<>();
        ServerTcpProtocl.directory = directory;
        ServerTcpProtocl.verbose = verbose;
        handShake();
    }

    public void handShake() throws IOException {
        if(verbose)
        {
            System.out.println("Waiting for connection request\n");
        }
        while(true)
        {

            //receiving request for connection
            DatagramPacket request = new DatagramPacket(new byte[1024],1024);
            this.connectionSocket.receive(request);
            Packet req = Packet.fromBytes(request.getData());
            if(!(req.getType() == 2)){
                continue;
            }
            InetAddress rAdd = request.getAddress();
            int rPort = request.getPort();
            InetAddress clientAddress = req.getPeerAddress();
            int clientPort = req.getPeerPort();

            //creating a handler for unique client request
            DatagramSocket datasocket ;
            if(socketMapping.containsKey(clientAddress)){
                 datasocket = socketMapping.get(clientAddress);
            }
            else {
                datasocket = new DatagramSocket();
                socketMapping.put(clientAddress, datasocket);
                if(verbose)
                {
                    System.out.println("Client handler created for client with address "+ clientAddress);
                }
                new Thread(new clientHandler(datasocket,clientAddress)).start();
            }

            //sending the syn-ack for the syn
            Packet p = new Packet(3,req.getSequenceNumber(),clientAddress,clientPort,Integer.toString(datasocket.getLocalPort()).getBytes());
            DatagramPacket response = new DatagramPacket(p.toBytes(), p.toBytes().length, rAdd, rPort);
            this.connectionSocket.send(response);


            //waiting for ACK
            try {
                TimeoutBlock timeoutBlock = new TimeoutBlock(20);//set timeout in milliseconds
                Runnable block=new Runnable() {

                    @Override
                    public void run() {

                        while(true) {
                            try {
                                connectionSocket.receive(request);
                                Packet req2 = null;
                                req2 = Packet.fromBytes(request.getData());
                                if (!(req2.getType() == 1)) {
                                    continue;
                                }
                                InetAddress clientAddress2 = req2.getPeerAddress();
                                int clientPort2 = req2.getPeerPort();
                                if(clientAddress2.equals(clientAddress)){
                                    if(clientPort2 == clientPort)
                                    {
                                        break;
                                    }
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }

                    }
                };

                timeoutBlock.addBlock(block);// execute the runnable block

            } catch (Throwable e) {

            }


        }


    }

    public static class clientHandler implements Runnable{

        private final DatagramSocket socket;
        private  InetAddress client;

        public clientHandler(DatagramSocket s,InetAddress c){
            socket = s;client=c;
        }

        @Override
        public void run() {
            String request = "";

                DatagramPacket req = new DatagramPacket(new byte[1024],1024);
                try {

                    if(verbose)
                    {
                        System.out.println("handler waiting for packets from client "+client);
                    }

                    //waiting for 1 packet
                    socket.receive(req);
                    Packet p = Packet.fromBytes(req.getData());
                    if(p.getType() == 6)
                    {
                        //close the connection
                        return;
                    }
                    if(p.getSequenceNumber() == 1 && p.getType() == 0)
                    {
                        String total = new String(p.getPayload());
                        Packet ack = new Packet(1,1L,p.getPeerAddress(),p.getPeerPort(),new byte[0]);
                        DatagramPacket ackP = new DatagramPacket(ack.toBytes(),ack.toBytes().length,req.getAddress(),req.getPort());
                        socket.send(ackP);
                        //sending ack for the packet length packet

                        if(verbose)
                        {
                            System.out.println("Handler "+client+": waiting for the request");
                        }
                        request = getRequestFromPacket(socket,Integer.parseInt(total.trim()));

                    }
                    System.out.println("Handler "+client+": request received");


                    String httpResponse = "";
                    String[] words = request.split(" ");
                    if (words[0].equals("GET")) {

                        httpResponse = HttpcLib.getResponse(request, directory,verbose);
                    } else {
                        httpResponse = HttpcLib.postResponse(request,directory,verbose);
                    }

                    if(verbose)
                    {
                        System.out.println("Handler "+client+": Response Created");
                        System.out.println("Handler "+client+": Sending the Response");
                    }

                    //response
                    requestresponse(socket,new InetSocketAddress(p.getPeerAddress(),p.getPeerPort()),new InetSocketAddress(req.getAddress(),req.getPort()),httpResponse);


                    System.out.println("Handler "+client+": Response Sent\n");


                } catch (IOException e) {
                    System.out.println("handler "+client+": Some error incurred");
                    throw new RuntimeException(e);
                }
                //removing the client from list of connection
                socketMapping.remove(client);
        }

        public static void requestresponse(DatagramSocket socket, InetSocketAddress serverAddr, InetSocketAddress routerAddr,String data) throws IOException {
            byte[] datab = data.getBytes();

            //converting the response to chunks
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
            //sending the size of the packets
            socket.send(size);

            //first packet ack
            byte[] pck2 = new byte[1024];
            DatagramPacket ack = new DatagramPacket(pck2, pck2.length);
            socket.setSoTimeout(30);

            while(true){
                try{
                    //waiting to see if the ack is received or no
                    socket.receive(ack);
                    Packet ackpacket = Packet.fromBytes(ack.getData());
                    if (ackpacket.getType() == 1 && ackpacket.getSequenceNumber()==1) {
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

                }catch (SocketTimeoutException s){
                    socket.send(size);
                    continue;
                }
            }

            socket.setSoTimeout(0);
            if(verbose)
            {
                System.out.println("handler " + serverAddr.getAddress()+": starting to send the packets of response");
            }
            sendreq(nofpackets, serverAddr, routerAddr,socket, chunks);
            if(verbose)
            {
                System.out.println("handler " + serverAddr.getAddress()+": Packets of response Sent");
            }
        }


        public static void sendreq(int nofpackets, InetSocketAddress serverAddr,InetSocketAddress routerAddr ,DatagramSocket socket, List<byte[]> chunks) throws IOException {
            try {
                windowRec = 100;
                lastackRec = 0;
                packets = new ArrayList<>();
                //send all the packets
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

                int tobesent = Math.min(nofpackets, windowRec);
                for (int i = 2; i <= tobesent + 1; i++) {
                    socket.send(packets.get(i - 2));
                    //start timer thread
                    Thread t = new Thread(new Timer(i, socket, 0));
                    t.start();
                }

                if(verbose)
                {
                    System.out.println("handler " + serverAddr.getAddress()+": Waiting for acks of all packets");
                }

                socket.setSoTimeout(30);
                while (lastackRec < nofpackets + 1) {
                    byte[] pck = new byte[1024];
                    DatagramPacket ack = new DatagramPacket(pck, pck.length);
//
                    socket.receive(ack);
                    Packet ackpacket = Packet.fromBytes(ack.getData());
                    if (ackpacket.getType() == 1 && ackpacket.getSequenceNumber() > lastackRec && ackpacket.getSequenceNumber() <= windowRec + 1) {
                        int newacs = (int) ackpacket.getSequenceNumber() - lastackRec + 1;

                        if (packets.size() > windowRec) {
                            for (int i = windowRec; i < windowRec + newacs; i++) {
                                if (packets.size() > i) {
                                    socket.send(packets.get(i));
                                    Thread t = new Thread(new Timer(i + 2, socket, 0));
                                    t.start();
                                    //start thread
                                } else
                                    break;
                            }

                        }

                        windowRec = windowRec + newacs;
                        lastackRec = (int) ackpacket.getSequenceNumber();
                    }

                    if (ackpacket.getType() == 4 && ackpacket.getSequenceNumber() > lastackRec && ackpacket.getSequenceNumber() <= windowRec + 1) {
                        socket.send(packets.get((int) ackpacket.getSequenceNumber() - 2));
                        Thread t = new Thread(new Timer((int) ackpacket.getSequenceNumber(), socket, 0));
                        t.start();
                        //thread
                    }

                }


            }
            catch (Exception e){
            }
            if(verbose)
            {
                System.out.println("Handler "+serverAddr.getAddress()+": Acks Received");
            }
        }

        public synchronized static void isAcked(int seq, DatagramSocket socket,int retries) throws IOException {
            if(!(seq<=lastackRec || retries >= 10)){
                //resend
                //create new thread
               // System.out.println("resending the packets");
                socket.send(packets.get(seq-2));
                Thread t = new Thread(new Timer(seq, socket, retries + 1));
                t.start();
            }
        }




        public String getRequestFromPacket(DatagramSocket socket,int totalPackets){
            int expseqNum = 2;
            int maxWindow = 100;
            int i;
            if(verbose)
            {
                System.out.println("handler "+client+": waiting for the request packets");
            }
            HashMap<Integer,byte[]> data = new HashMap<>();
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
                                    //sending ack
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
                                    //sending nack
                                    Packet nack = new Packet(4, i, clientAddress, clientPort, new byte[0]);
                                    DatagramPacket nackP = new DatagramPacket(nack.toBytes(), nack.toBytes().length, rAddress, rPort);
                                    socket.send(nackP);
                                }
                            }
                        }
                        if(pSeq < expseqNum)
                        {
                            //sending ack for previous
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
            if(verbose)
            {
                System.out.println("handler "+client+": Received all request packets");
            }
            request = new String(reqArr);
            return request.trim();
        }

    }
}
