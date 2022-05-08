package map_generator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

public class MapGenerator {
	public static final String fileName = "C:\\Learning\\eclipse-workspace\\DK64MapGenerator\\meshes\\SM.csv";
	public static final String dirOut = "C:\\Users\\Jacob\\Desktop\\BK-Mesh\\";
	
	public static ArrayList<Vertex> verts;
	public static ArrayList<Triangle> tris;
	
	public static byte[] 	geometryHeader,
							geometryVertices,
							geometryF3DEX2,
							geometryFooter;
	
	public static String floorSize, wallSize;
	
	public static int f3dex2Size;
	
	public static void main(String[] args) throws IOException, FileNotFoundException {
		File file = new File(fileName);
		generateGeometryFileVertices(file);
		generateGeometryFileFaces(file);
		generateFloorAndWallFiles();
		generateHeaderAndFooter();
		generateGeometryFile();
	}

	public static void generateGeometryFileVertices(File f) throws IOException {
		ArrayList<Byte> byteList = new ArrayList<>();
		ArrayList<Vertex> points = new ArrayList<>();
		Scanner scan = new Scanner(f);
		while(scan.hasNextLine()) {
			String[] line = scan.nextLine().trim().split(",");
			if(line[0].equals("vertices")) continue;
			else if(line[0].equals("faces")) break;
			
			byte[] bArray = new BigInteger(String.format("%04x%04x%04x000000000000000000FF",Integer.parseInt(line[0]), 
																							Integer.parseInt(line[1]),
																							Integer.parseInt(line[2])),16).toByteArray();
			System.out.println(String.format("%04x%04x%04x000000000000000000FF",Integer.parseInt(line[0]), 
																							Integer.parseInt(line[1]),
																							Integer.parseInt(line[2])));
			points.add(new Vertex(Integer.parseInt(line[0]), Integer.parseInt(line[1]), Integer.parseInt(line[2])));
			for(int i=0; i<(16 - bArray.length); ++i) byteList.add((byte)0); //pad if leading 00s get truncated
			for(byte b: bArray)
				byteList.add(b);
		}
		byte[] data = new byte[byteList.size()];
		for(int i=0; i<byteList.size(); ++i) data[i] = byteList.get(i).byteValue();
		
		geometryVertices = data;
		
		FileOutputStream fos = new FileOutputStream(dirOut+"geometry-file-vertices.bin");
		fos.write(data);
		fos.flush();
		fos.close();
		
		verts = points;
	}
	
	public static void generateGeometryFileFaces(File f) throws IOException {
		ArrayList<Byte> byteList = new ArrayList<>();
		ArrayList<Triangle> triData = new ArrayList<>();
		
		Map<String,String> materialMap = new HashMap<>();
		
		Scanner scan = new Scanner(f);
		while(scan.hasNextLine()) {
			String[] line = scan.nextLine().trim().split(",");
			if(line[0].equals("faces")) break;
		}
		int buffer_chunk = -1; //index div 32
		String prev_color = " ";
		int index=0;
		byte[] vertArray = new BigInteger("0102004006000000",16).toByteArray(); //start loading vertices at index 0
		byte[] colorArray = new BigInteger("FC7EA004100C00F4",16).toByteArray(); //set color combine mode
		byte[] geometryModeArray = new BigInteger("D900000000000401",16).toByteArray(); //set Z-DEPTH
		byte[] otherModeArray = new BigInteger("E200031D00552230",16).toByteArray(); //set surfaces to opaque
		for(int i=1; i<colorArray.length; ++i) byteList.add(colorArray[i]);
		for(int i=1; i<geometryModeArray.length; ++i) byteList.add(geometryModeArray[i]);
		for(int i=1; i<otherModeArray.length; ++i) byteList.add(otherModeArray[i]);
		for(byte b: vertArray) byteList.add(b);
		while(scan.hasNextLine()) {
			String[] line = scan.nextLine().split(",");
			if(line.length < 1 || line[0].equals("")) continue;
			
			//Handle face-color
			if(line[0].contains("material")) {
				String color = materialMap.get(line[0]);
				if(color == null || color.equals("")) {
					color = generateRandomColor();
					materialMap.put(line[0], color);
				}
				while(color.length() < 8) color="0"+color;//pad with 0s
				colorArray = new BigInteger("FA000000"+color,16).toByteArray();//set primary color
				for(int i=1; i<colorArray.length; ++i) byteList.add(colorArray[i]);
				System.out.println("********"+"FA000000"+color);
				continue;
			}
			
			System.out.println("Face "+(index++)+" buffer chunk: "+buffer_chunk);
			//Fix 1-indexing
			line[0] = ""+(Integer.parseInt(line[0])-1);
			line[1] = ""+(Integer.parseInt(line[1])-1);
			line[2] = ""+(Integer.parseInt(line[2])-1);
			//Handle vertices
			if(	Integer.parseInt(line[0])/32 == Integer.parseInt(line[1])/32 &&
				Integer.parseInt(line[0])/32 == Integer.parseInt(line[2])/32) {
					if(buffer_chunk != Integer.parseInt(line[0])/32) {
						buffer_chunk = Integer.parseInt(line[0])/32;
						String segmented_addr = String.format("%06x", buffer_chunk*32*16);
						vertArray = new BigInteger("0102004006"+segmented_addr,16).toByteArray(); //start loading vertices at index 0
						for(byte b: vertArray) byteList.add(b);
						System.out.println("***"+"0102004006"+segmented_addr);
					}
			} else {
				String segmented_addr1 = String.format("%06x", Integer.parseInt(line[0])*16),
						segmented_addr2 = String.format("%06x", Integer.parseInt(line[1])*16),
						segmented_addr3 = String.format("%06x", Integer.parseInt(line[2])*16);
				vertArray = new BigInteger(	"0100100206"+segmented_addr1+
											"0100100406"+segmented_addr2+
											"0100100606"+segmented_addr3,16).toByteArray(); //start loading vertices at index 0
				for(byte b: vertArray) byteList.add(b);
				System.out.println(	"***0100100206"+segmented_addr1+
									"***0100100406"+segmented_addr2+
									"***0100100606"+segmented_addr3);
				
				//add simplified G_TRI command
				byte[] bArray = new BigInteger("0500020400000000",16).toByteArray();
				System.out.println("0500020400000000");
				Vertex 	v1 = verts.get(Integer.parseInt(line[0])),
						v2 = verts.get(Integer.parseInt(line[1])),
						v3 = verts.get(Integer.parseInt(line[2]));
				triData.add(new Triangle(	v1.x, v1.y, v1.z,
											v2.x, v2.y, v2.z,
											v3.x, v3.y, v3.z));
				for(int i=0; i<(8 - bArray.length); ++i) byteList.add((byte)0); //pad if leading 00s get truncated
				for(byte b: bArray)
					byteList.add(b);
				
				buffer_chunk = -1; //reset buffer_chunk so it will get a load vertex instruction
				continue;
			}
			
			byte[] bArray = new BigInteger(String.format("05%02x%02x%02x00000000",(Integer.parseInt(line[0])*2)%64, 
																				  (Integer.parseInt(line[1])*2)%64,
																				  (Integer.parseInt(line[2])*2)%64),16).toByteArray();
			System.out.println(String.format("05%02x%02x%02x00000000",(Integer.parseInt(line[0])*2)%64, 
																	  (Integer.parseInt(line[1])*2)%64,
																	  (Integer.parseInt(line[2])*2)%64));
			//points.add(new Vertex(Integer.parseInt(line[0]), Integer.parseInt(line[1]), Integer.parseInt(line[2])));
			Vertex 	v1 = verts.get(Integer.parseInt(line[0])),
					v2 = verts.get(Integer.parseInt(line[1])),
					v3 = verts.get(Integer.parseInt(line[2]));
			triData.add(new Triangle(v1.x, v1.y, v1.z,
									 v2.x, v2.y, v2.z,
									 v3.x, v3.y, v3.z));
			for(int i=0; i<(8 - bArray.length); ++i) byteList.add((byte)0); //pad if leading 00s get truncated
			for(byte b: bArray)
				byteList.add(b);
		}
		
		//End DL
		byte[] endDLArray = new BigInteger("DF00000000000000",16).toByteArray(); //end display list
		for(int i=1; i<endDLArray.length; ++i) byteList.add(endDLArray[i]);
		
		byte[] data = new byte[byteList.size()];
		for(int i=0; i<byteList.size(); ++i) data[i] = byteList.get(i).byteValue();
		
		f3dex2Size = byteList.size();
		geometryF3DEX2 = data;
		
		FileOutputStream fos = new FileOutputStream(dirOut+"geometry-file-faces.bin");
		fos.write(data);
		fos.flush();
		fos.close();
		
		tris = triData;
	}
	
	public static void generateHeaderAndFooter() throws IOException {
		Path path = Paths.get("C:\\Learning\\eclipse-workspace\\DK64MapGenerator\\setup\\footer.bin");
		byte[] footer =  Files.readAllBytes(path);
		
		geometryFooter = footer;
		
		path = Paths.get("C:\\Learning\\eclipse-workspace\\DK64MapGenerator\\setup\\header.bin");
		byte[] header = Files.readAllBytes(path);
		
		byte[] 	floorSizeBytes = new BigInteger(floorSize,16).toByteArray(),
				wallSizeBytes = new BigInteger(wallSize,16).toByteArray(),
				f3dex2SizeBytes = new BigInteger(String.format("%08x",f3dex2Size+0x140),16).toByteArray(),
				footerBytes = new BigInteger(String.format("%08x",verts.size()*16+f3dex2Size+0x140),16).toByteArray();
		int index = 4 - wallSizeBytes.length;
		for(byte b: wallSizeBytes) header[index++] = b;
		index = 8 - floorSizeBytes.length;
		for(byte b: floorSizeBytes) header[index++] = b;
		
		index = 60 - f3dex2SizeBytes.length;
		for(byte b: f3dex2SizeBytes) header[index++] = b;
		
		index = 68 - footerBytes.length;
		for(byte b: footerBytes) header[index++] = b;
		int[] addressOffsets = {4,4,4,4,4,4,4,16,0,16,0,4};
		index = 72;
		BigInteger nextAddress = new BigInteger(String.format("%08x",verts.size()*16+f3dex2Size+0x140),16);
		for(int i=0; i<addressOffsets.length; ++i) {
			nextAddress = nextAddress.add(new BigInteger(""+addressOffsets[i]));
			footerBytes = nextAddress.toByteArray();
			index = index - footerBytes.length;
			for(byte b: footerBytes) header[index++] = b;
			index+=4;
		}
		geometryHeader = header;
	}
	
	public static void generateGeometryFile() throws IOException {
		FileOutputStream fos = new FileOutputStream(dirOut+"geometry.bin");
		ArrayList<Byte> byteList = new ArrayList<>();
		for(byte b: geometryHeader) byteList.add(b);
		for(byte b: geometryF3DEX2) byteList.add(b);
		for(byte b: geometryVertices) byteList.add(b);
		for(byte b: geometryFooter) byteList.add(b);
		byte[] output = new byte[byteList.size()];
		for(int i=0; i<byteList.size(); ++i) output[i] = byteList.get(i);
		fos.write(output);
		fos.flush();
		fos.close();
	}
	
	public static void generateFloorAndWallFiles() throws IOException {
		ArrayList<Byte> floorTriBytes = new ArrayList<>(),
						wallTriBytes = new ArrayList<>();
		int numWallTris = 0,
			numFloorTris = 0;
		for(int i=0; i<tris.size(); ++i) {
			Triangle t = tris.get(i);
			System.out.println(i);
			t.setFacingAngle();
			/*if(t.x[0] == 1179 && t.y[0] == 91 && t.z[0] == 1023) {
				try{
					System.out.println(t.x[0]+" "+t.y[0]+" "+t.z[0]+"\n"+
							t.x[1]+" "+t.y[1]+" "+t.z[1]+"\n"+
							t.x[2]+" "+t.y[2]+" "+t.z[2]+" ");
					Thread.sleep(10000);
				} catch(Exception e) {}
			}*/
			if(t.isWall) {
				byte[] wallArray = new BigInteger(String.format("%04x%04x%04x%04x%04x%04x%04x%04x%04x%04x%02xFF0018",t.x[0],t.y[0],t.z[0], 
						t.x[1],t.y[1],t.z[1], 
						t.x[2],t.y[2],t.z[2],
						t.facingAngle,t.directionBit),16).toByteArray();
				for(int j=0; j<(24 - wallArray.length); ++j) wallTriBytes.add((byte)0); //pad if leading 00s get truncated
				for(byte b: wallArray)
					wallTriBytes.add(b);
				System.out.println(String.format("w %04x%04x%04x%04x%04x%04x%04x%04x%04x000000FF0018",t.x[0],t.y[0],t.z[0], 
						t.x[1],t.y[1],t.z[1], 
						t.x[2],t.y[2],t.z[2]));
				numWallTris++;
			}
			byte[] floorArray = new BigInteger(String.format("%04x%04x%04x%04x%04x%04x%04x%04x%04x000001000F70",t.x[0]*6,t.x[1]*6,t.x[2]*6, 
																											t.y[0]*6,t.y[1]*6,t.y[2]*6, 
																											t.z[0]*6,t.z[1]*6,t.z[2]*6),16).toByteArray();
			for(int j=0; j<(24 - floorArray.length); ++j) floorTriBytes.add((byte)0); //pad if leading 00s get truncated
			for(byte b: floorArray)
				floorTriBytes.add(b);
			System.out.println(String.format("f %04x%04x%04x%04x%04x%04x%04x%04x%04x000001000F70",t.x[0]*6,t.x[1]*6,t.x[2]*6, 
					t.y[0]*6,t.y[1]*6,t.y[2]*6, 
					t.z[0]*6,t.z[1]*6,t.z[2]*6));
			numFloorTris++;
		}
		floorSize = String.format("%08x", numFloorTris*24+8);
		wallSize = String.format("%08x", numWallTris*24+8);
		
		ArrayList<Byte> floorHeader = new ArrayList<Byte>(),
						wallHeader = new ArrayList<Byte>();
		byte[] floorHeaderBytes = new BigInteger(String.format("%08x%08x",numFloorTris,numFloorTris*24+8),16).toByteArray(),
				wallHeaderBytes = new BigInteger(String.format("%08x%08x",numWallTris,numWallTris*24+8),16).toByteArray();
		
		System.out.println("Tris:"+tris.size()+"\nVerts:"+verts.size());
		System.out.println("Walls: "+numWallTris+"\nFloors: "+numFloorTris);
		
		for(int i=0; i<(8 - floorHeaderBytes.length); ++i) floorHeader.add((byte)0); //pad if leading 00s get truncated
		for(int i=0; i<(8 - wallHeaderBytes.length); ++i) wallHeader.add((byte)0); //pad if leading 00s get truncated
		for(byte b: floorHeaderBytes)
			floorHeader.add(b);
		for(byte b: wallHeaderBytes)
			wallHeader.add(b);
		for(int i=0; i<floorHeader.size(); ++i) {
			floorTriBytes.add(i,floorHeader.get(i));
			wallTriBytes.add(i,wallHeader.get(i));
		}
		byte[] floorData = new byte[floorTriBytes.size()],
				wallData = new byte[wallTriBytes.size()];
		for(int i=0; i<floorTriBytes.size(); ++i) floorData[i] = floorTriBytes.get(i).byteValue();
		for(int i=0; i<wallTriBytes.size(); ++i) wallData[i] = wallTriBytes.get(i).byteValue();
		
		FileOutputStream fos = new FileOutputStream(dirOut+"floors.bin"),
				fos2 = new FileOutputStream(dirOut+"walls.bin") ;
		fos.write(floorData);
		fos.flush();
		fos.close();
		
		fos2.write(wallData);
		fos2.flush();
		fos2.close();
	}
	
	public static String generateRandomColor() {
		String r = Integer.toHexString(new Random().nextInt(240)+10),
				g = Integer.toHexString(new Random().nextInt(240)+10),
				b = Integer.toHexString(new Random().nextInt(240)+10);
		return r+g+b+"FF";
	}

}

class Vertex {
	int x,y,z;
	public Vertex(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
}

class Triangle {
	int[] x = new int[3];
	int[] y = new int[3];
	int[] z = new int[3];
	boolean isWall = false;
	int facingAngle = 0;
	int directionBit = 0;
	public Triangle(int x1, int y1, int z1,
					int x2, int y2, int z2,
					int x3, int y3, int z3) {
		x[0] = x1; x[1] = x2; x[2] = x3;
		y[0] = y1; y[1] = y2; y[2] = y3;
		z[0] = z1; z[1] = z2; z[2] = z3;
	}
	
	public void setFacingAngle() {
		Vector3d v1 = new Vector3d(x[1] - x[0], y[1] - y[0], z[1] - z[0]);
		Vector3d v2 = new Vector3d(x[2] - x[0], y[2] - y[0], z[2] - z[0]);
		Vector3d cp = new Vector3d(); cp.cross(v1, v2);
		System.out.println(cp);
		Vector3d norm = new Vector3d(cp.x,cp.y,cp.z);
		norm.normalize();
		
		if(Math.abs(Math.toDegrees(norm.angle(new Vector3d(0,-1,0)))) < 120.0) {
			isWall = true;
		}
		System.out.println(norm);
		System.out.println(Math.toDegrees(Math.atan2(norm.x, norm.z)));
		double angle = (Math.toDegrees(Math.atan2(norm.x,norm.z)) + 360) % 360;
		if(angle < 180) {
			angle+=180;
			directionBit = 0;
		} else {
			directionBit = 1;
		}
		
		//System.out.println("Angle: "+angle);
		int DK64Angle = (int)(angle/360 * 4096);
		System.out.println(isWall+"\n"+angle);
		//System.out.println(DK64Angle);
		facingAngle = DK64Angle;
	}
}
