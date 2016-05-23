
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Random;

/**
 * Author: Andrew Jarombek
 * Date: 3/7/2016 - 3/29/2016
 * The Client Side of the UFT data transfer.
 */
public class UFTClient {

    public static void main(String[] args) {

        // Get the command line inputs
        String hostname = args[0];
        String fn = args[1];
        File file = new File(fn);
        int byteNum = Integer.parseInt(args[2]);
        double corruptionRate = Double.parseDouble(args[3]);
        double lossRate = Double.parseDouble(args[4]);

        // Server Stats
        int totalPackets = 0;
        int dupPackets = 0;
        int totalAcks = 0;
        int dupAcks = 0;
        int timeouts = 0;

        // Make sure the corruption rate is valid
        if (corruptionRate < 0 || corruptionRate > 1) {
            System.out.println("ERROR: Invalid Corruption Rate");
            System.exit(0);
        }

        // Make sure the loss rate is valid
        if (lossRate < 0 || lossRate > 1) {
            System.out.println("ERROR: Invalid Loss Rate");
            System.exit(0);
        }

        // Make sure a valid byte number is entered
        if (byteNum > 1000 || byteNum <= 0) {
            System.out.println("ERROR: Invalid Number of Bytes Specified (Maximum = 1000).");
            System.exit(0);
        }

        // Create a UDP socket to send to the UDP segment out
        DatagramSocket clientSocket = null;
        try {
            clientSocket = new DatagramSocket();
            clientSocket.setSoTimeout(200);
        } catch (SocketException e) {
            System.out.println("ERROR: Unable to Create UDP Socket.");
            System.exit(0);
        }

        // convert server address from string to InetAddress
        InetAddress serverInetAddress = null;
        try {
            serverInetAddress = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            System.out.println("ERROR: Hostname Given Unknown.");
            System.exit(0);
        }

        // Create a FileInputStream to read the file bytes
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);

            // byte data from the file read
            byte[] bytes = new byte[byteNum+3];

            // While there are still bytes to read in the file
            int sequenceNumber = 1;
            String lastSequenceNumber = "00";
            int finalPacket = 0;
            while (input.read(bytes, 3, byteNum) != -1) {

                // First two bytes of header are the sequence number
                bytes[0] = (byte) (sequenceNumber / 10);
                bytes[1] = (byte) (sequenceNumber % 10);
                String sentSeqNum = String.valueOf(bytes[0]) + bytes[1];
                // Third byte of header tell whether or not this is the last packet (0=false, 1=true)
                bytes[2] = (input.available() > 0) ? (byte) (finalPacket) : (byte) (finalPacket+1);

                // create the UDP segment
                DatagramPacket dataPacket = new DatagramPacket(bytes, byteNum+3, serverInetAddress, 22600);

                boolean ack = false;
                boolean failed = false;

                // While the server has not sent an acknowledgement, keep sending the same data
                while (!ack) {
                    // Loss and corruption simulation
                    boolean corrupt = Simulate.isCorrupt(corruptionRate);
                    boolean lost = Simulate.isLost(lossRate);

                    // add to statistics
                    totalPackets++;

                    // Simulate the chance of a lost packet
                    if (lost) {
                        System.out.println("Lost Packet #" + sentSeqNum);
                        System.out.println("Timeout for Packet #" + sentSeqNum);
                        timeouts++;
                        failed = true;
                    } else {
                        // Check if this packet has already been sent once
                        if (!failed) {
                            System.out.println("Sent Packet #" + sentSeqNum);
                        } else {
                            System.out.println("Retransmitted Packet #" + sentSeqNum);
                            dupPackets++;
                        }

                        // send UDP segment using the UDP socket
                        try {
                            clientSocket.send(dataPacket);
                        } catch (IOException e) {
                            System.out.println("ERROR: Segment Not Sent.");
                            System.exit(0);
                        }

                        // create a byte array to store the data received from the server
                        byte[] buffer = new byte[2];

                        // allocate a datagram packet for receiving
                        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);

                        // receive a packet from the server
                        // remember this blocks until data is received
                        try {
                            clientSocket.receive(receivePacket);
                            totalAcks++;

                            // Simulate a received corrupt packet
                            if (corrupt) {
                                System.out.println("Received a Corrupt ACK");
                                failed = true;
                            } else {

                                // retrieve the actual bytes received in the receivePacket
                                byte[] receivedBytes = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
                                String receivedSeqNum = String.valueOf(receivedBytes[0]) + receivedBytes[1];

                                // If there was an error on the server side
                                if (receivedSeqNum.equals(lastSequenceNumber)) {
                                    System.out.println("Received Ack #" + receivedSeqNum);
                                    dupAcks++;
                                    failed = true;
                                } else {
                                    // If there are no errors on server side or client side
                                    System.out.println("Received Ack #" + receivedSeqNum);
                                    lastSequenceNumber = sentSeqNum;
                                    sequenceNumber++;
                                    ack = true;
                                }
                            }

                        } catch (SocketTimeoutException e) {
                            System.out.println("Timeout for Packet #" + sentSeqNum);
                            timeouts++;
                            failed = true;
                        }
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("ERROR: Unable to Read File.");
            System.exit(0);
        }

        // Print out the statistics of the data transfer
        System.out.println("\n+---------------STATS---------------+");
        System.out.println("Total Packets Transmitted: " + totalPackets);
        System.out.println("Retransmitted Packets: " + dupPackets);
        System.out.println("Total ACK Received: " + totalAcks);
        System.out.println("Duplicate ACK Received: " + dupAcks);
        System.out.println("Total # Of Timeouts: " + timeouts + "\n");

        // close the socket
        clientSocket.close();
    }

}
