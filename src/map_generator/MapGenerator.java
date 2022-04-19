package map_generator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

public class MapGenerator {
	public static final String fileName = "C:\\Learning\\eclipse-workspace\\DK64MapGenerator\\meshes\\SM.csv";
	public static final String dirOut = "C:\\Users\\Jacob\\Desktop\\BK-Mesh\\";
	
	public static ArrayList<Vertex> verts;
	public static ArrayList<Triangle> tris;
	
	public static void main(String[] args) throws IOException, FileNotFoundException {
		File file = new File(fileName);
		generateGeometryFileVertices(file);
		generateGeometryFileFaces(file);
		generateFloorFile();
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
		
		FileOutputStream fos = new FileOutputStream(dirOut+"geometry-file-faces.bin");
		fos.write(data);
		fos.flush();
		fos.close();
		
		tris = triData;
	}
	
	public static void generateFloorFile() throws IOException {
		ArrayList<Byte> byteList = new ArrayList<>(),
						byteList2 = new ArrayList<>();
		/*int next_mesh_pointer=8; //start with 8
		int modulus = 16; //number of tris to segment each mesh into
		int size_of_tri_struct = 24; //size of the triangles in the floors file*/
		for(int i=0; i<tris.size(); ++i) {
			//every 32 tris, start a new mesh definition
			/*if(i%modulus == 0) {
				byte[] meshPtr;
				if(tris.size() - i < modulus) {
					meshPtr = new BigInteger(String.format("%08x",next_mesh_pointer+(size_of_tri_struct*(tris.size() - i))),16).toByteArray();
				} else {
					meshPtr = new BigInteger(String.format("%08x",next_mesh_pointer+(size_of_tri_struct*modulus)),16).toByteArray();
					next_mesh_pointer+= size_of_tri_struct*modulus+4;
				}
				for(int j=0; j<(4-meshPtr.length); ++j)byteList.add((byte)0);
				for(int j=0; j<meshPtr.length; ++j) {
					byteList.add(meshPtr[j]);
					System.out.print(meshPtr[j]);
				}
				System.out.println();
			}*/
			Triangle t = tris.get(i);
			byte[] bArray = new BigInteger(String.format("%04x%04x%04x%04x%04x%04x%04x%04x%04x000001000F70",t.x[0]*6,t.x[1]*6,t.x[2]*6, 
																											t.y[0]*6,t.y[1]*6,t.y[2]*6, 
																											t.z[0]*6,t.z[1]*6,t.z[2]*6),16).toByteArray();
			byte[] bArray2 = new BigInteger(String.format("%04x%04x%04x%04x%04x%04x%04x%04x%04x0BFE01FF0018",t.x[0],t.x[1],t.x[2], 
					t.y[0],t.y[1],t.y[2], 
					t.z[0],t.z[1],t.z[2]),16).toByteArray();

			System.out.println(String.format("f %04x%04x%04x%04x%04x%04x%04x%04x%04x000001000F70",t.x[0]*6,t.x[1]*6,t.x[2]*6, 
					t.y[0]*6,t.y[1]*6,t.y[2]*6, 
					t.z[0]*6,t.z[1]*6,t.z[2]*6));
			System.out.println(String.format("w %04x%04x%04x%04x%04x%04x%04x%04x%04x0BFE01FF0018",t.x[0],t.x[1],t.x[2], 
					t.y[0],t.y[1],t.y[2], 
					t.z[0],t.z[1],t.z[2]));
			for(int j=0; j<(24 - bArray.length); ++j) byteList.add((byte)0); //pad if leading 00s get truncated
			for(int j=0; j<(24 - bArray2.length); ++j) byteList2.add((byte)0); //pad if leading 00s get truncated
			for(byte b: bArray)
				byteList.add(b);
			for(byte b: bArray2)
				byteList2.add(b);
		}
		ArrayList<Byte> header = new ArrayList<Byte>();
		byte[] bArray = new BigInteger(String.format("%08x%08x",tris.size(),tris.size()*24+8),16).toByteArray();
		System.out.println(String.format("%08x%08x",15,tris.size()*24+8));

		
		//System.out.println(tris.size()/modulus);
		for(int i=0; i<(8 - bArray.length); ++i) header.add((byte)0); //pad if leading 00s get truncated
		for(byte b: bArray)
			header.add(b);
		for(int i=0; i<header.size(); ++i) {
			byteList.add(i,header.get(i));
			byteList2.add(i,header.get(i));
		}
		byte[] data = new byte[byteList.size()],
				data2 = new byte[byteList2.size()];
		for(int i=0; i<byteList.size(); ++i) data[i] = byteList.get(i).byteValue();
		for(int i=0; i<byteList2.size(); ++i) data2[i] = byteList2.get(i).byteValue();
		
		System.out.println("Tris:"+tris.size()+"\nVerts:"+verts.size());
		
		FileOutputStream fos = new FileOutputStream(dirOut+"floors.bin"),
						fos2 = new FileOutputStream(dirOut+"walls.bin") ;
		fos.write(data);
		fos.flush();
		fos.close();
		
		fos2.write(data2);
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
	public Triangle(int x1, int y1, int z1,
					int x2, int y2, int z2,
					int x3, int y3, int z3) {
		x[0] = x1; x[1] = x2; x[2] = x3;
		y[0] = y1; y[1] = y2; y[2] = y3;
		z[0] = z1; z[1] = z2; z[2] = z3;
	}
}
