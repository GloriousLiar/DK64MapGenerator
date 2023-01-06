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
	public static final String fileName = "E:\\dev\\eclipse\\DK64MapGenerator\\blender-output\\model_sm_w_ci4.c";
	public static final String dirOut = "C:\\Users\\Jacob\\Desktop\\spiral2\\";
	
	public static ArrayList<Vertex> verts;
	public static ArrayList<Triangle> tris;
	
	public static byte[] 	geometryHeader,
							geometryVertices,
							geometryF3DEX2,
							geometryFooter;
	
	public static String floorSize, wallSize;
	
	public static int f3dex2Size;
	
	
	//blender stuff
	public static ArrayList<String> vtxSegments,
									triSegments,
									materialSegments,
									imageSegments;
	
	public static Map<String,Integer> vertexBlocks;
	
	public static String mesh_name = "spiral_mountain_export";
	
	
	public static void main(String[] args) throws IOException, FileNotFoundException {
		/* Uncomment for level geometry
		 
		File file = new File(fileName);
		generateGeometryFileVertices(file);
		generateGeometryFileFaces(file);
		generateFloorAndWallFiles();
		generateHeaderAndFooter();
		generateGeometryFile();*/
		
		/*File file = new File(fileName);
		ModelGenerator.dirOut = dirOut;
		ModelGenerator.generateGeometryFileVertices(file);
		ModelGenerator.generateGeometryFileFaces(file);
		ModelGenerator.generateHeaderAndFooter();
		ModelGenerator.generateGeometryFile();*/
		
		File file = new File(fileName);
		parseGfx(file);
		parseImages();
		//parseVertices();
		//translateVerticesToPositiveXZ();
		//parseFaces();
		//generateFloorAndWallFiles();
		//rebuildFile();
	}

	public static void parseGfx(File f) throws IOException {
		Scanner scan = new Scanner(f);
		String whole_file = "";
		while(scan.hasNextLine()) {
			whole_file+=scan.nextLine();
		}
		String[] segments = whole_file.split(";");
		//System.out.println(segments.length);
		
		vtxSegments = new ArrayList<String>();
		triSegments = new ArrayList<String>();
		materialSegments = new ArrayList<String>();
		imageSegments = new ArrayList<String>();
		for(String s: segments) {
			if(s.contains("cull") || s.contains("Cull")) continue;
			if(s.contains("Gfx")) {
				if(s.contains("revert") || s.contains("Revert")) continue;
				else if(s.contains("tri_")) {
					triSegments.add(s);
				} else if(s.contains("mat")) {
					materialSegments.add(s);
				}
			} else if(s.contains("Vtx")){
				vtxSegments.add(s);
			} else {
				imageSegments.add(s);
			}
		}
		
		//find material_null
		int null_index=-1;
		for(int i=0; i<materialSegments.size(); ++i) {
			if(materialSegments.get(i).contains("material_null")) {
				null_index = i;
				break;
			}
		}
		materialSegments.remove(null_index);
		vtxSegments.remove(null_index);
		triSegments.remove(null_index);
	}
	
	public static void parseImages() throws IOException {
		Files.createDirectories(Paths.get(dirOut+"/textures"));
		
		FileOutputStream fos = new FileOutputStream(dirOut+"build_imports.txt");
		String out = "";
		int index = 6013; //first unused index in texture table
		for(String s: imageSegments) {
			if(s.contains("_ci4_pal_") || s.contains("_ci4[]")) { //c14 and/or pallette
				int 	start = s.indexOf(mesh_name) + mesh_name.length() + 1,
						end = 	s.indexOf("_ci4");
				while(s.substring(start).startsWith("_")) start++;
				
				boolean pallette = s.contains("ci4_pal");
				String 	format = pallette ? "rgba5551" : "ia4",
						name = pallette ? "_pal" : "";
				
				out+= "\t{\n"+
						"\t\t\"name\": \""+s.substring(start,end)+name+"\",\n" +
						"\t\t\"pointer_table_index\": 25,\n" +
						"\t\t\"file_index\": "+(index++)+",\n" +
						"\t\t\"source_file\": \"bin/"+s.substring(start,end)+name+".bin\",\n" +
						"\t\t\"do_not_extract\": True\n" +
					  "\t},\n";
				
				exportImage(s, s.substring(start,end)+name);
			} else { //rgba16
				int 	start = s.indexOf(mesh_name) + mesh_name.length() + 1,
						end = 	s.indexOf("_rgba16");
				while(s.substring(start).startsWith("_")) start++;
				
				out+= "\t{\n"+
						"\t\t\"name\": \""+s.substring(start,end)+"\",\n" +
						"\t\t\"pointer_table_index\": 25,\n" +
						"\t\t\"file_index\": "+(index++)+",\n" +
						"\t\t\"source_file\": \"bin/"+s.substring(start,end)+".bin\",\n" +
						"\t\t\"texture_format\": \"rgba5551\"\n" +
					  "\t},\n";
				
				exportImage(s, s.substring(start,end));
			}
		}
		fos.write(out.getBytes());
		fos.flush();
		fos.close();
	}
	
	public static void exportImage(String imageSegment, String name) throws IOException{
		FileOutputStream fos = new FileOutputStream(dirOut+"textures\\"+name+".bin");
		
		String[] imageBytes = imageSegment.split("[{},]");
		for(String s: imageBytes) System.out.print(s+"*");
		System.out.println();
		//System.out.println(imageSegment);
		
		int bytes = 0;
		for(int i=1; i<imageBytes.length; ++i) {
			if(!imageBytes[i].contains("0x")) continue;
			String hex = imageBytes[i].substring(imageBytes[i].indexOf("0x")+2).trim();
			byte[] bArray = new byte[8];
			for(int j=0; j<hex.length(); j+=2) {
				bArray[j/2] = (byte) ((Character.digit(hex.charAt(j), 16) << 4) + Character.digit(hex.charAt(j+1), 16));
			}
			fos.write(bArray);
			
			//if(!imageBytes[i].contains("0x")) continue;
			//System.out.println(imageBytes[i].substring(imageBytes[i].indexOf("0x")+2).trim());
			//String hex = imageBytes[i].substring(imageBytes[i].indexOf("0x")+2).trim();
			//fos.write(new BigInteger(hex,16).toByteArray());
			//bytes += new BigInteger(hex,16).toByteArray().length;
			//System.out.println(bytes);
			//for(byte b: new BigInteger(hex,16).toByteArray()) System.out.print(b+" ");
			//System.out.println();
			//if(new BigInteger(hex,16).toByteArray().length != 8) {
			//	System.out.println("bad"+new BigInteger(hex,16).toByteArray().length);
			//	System.out.println(hex);
			//	for(byte b: new BigInteger(hex,16).toByteArray()) System.out.print(b+" ");
			//	System.out.println();
			//}
		}
		
		fos.flush();
		fos.close();		
	}
	
	public static void parseVertices() throws IOException {
		vertexBlocks = new HashMap<String,Integer>();
		verts = new ArrayList<Vertex>();
		int cumulative_vertices=0;
		for(String segment: vtxSegments) {
			//get name for map
			int start = segment.indexOf("Vtx")+4;
			int end = segment.indexOf("[");
			vertexBlocks.put(segment.substring(start,end), cumulative_vertices);
			//System.out.println(segment.substring(start,end)+" "+cumulative_vertices);
			
			segment = segment.substring(segment.indexOf("{")+1);
			while(segment.contains("{{ {")) {
				start = segment.indexOf("{{ {");
				end = segment.indexOf("} }},");
				String[] vtx_n = segment.substring(start,end).split("[\\{\\}\\s,]+");
				
				verts.add(new Vertex(	Integer.parseInt(vtx_n[1]),
										Integer.parseInt(vtx_n[2]),
										Integer.parseInt(vtx_n[3])));
				segment = segment.substring(end+5);
				cumulative_vertices++;
			}
		}
	}

	public static void translateVerticesToPositiveXZ() throws IOException {
		int min_x=Integer.MAX_VALUE, min_z=Integer.MAX_VALUE;
		for(Vertex v: verts) {
			if(v.x < min_x) min_x = v.x;
			if(v.z < min_z) min_z = v.z;
		}
		if(min_x >= 0 && min_z >= 0) return; //already +XZ
		System.out.println("MINS: "+min_x+" "+min_z);
		
		//update the vert blocks in the model.c file
		ArrayList<String> newSegments = new ArrayList<String>();
		for(String segment: vtxSegments) {
			ArrayList<Integer> 	starts = new ArrayList<Integer>(),
								ends = new ArrayList<Integer>();
			for(int index = segment.indexOf("{{ {"); index >= 0; index = segment.indexOf("{{ {", index+1)) {
				starts.add(index);
				ends.add(segment.indexOf("},",index));
				System.out.println(index+" "+segment.indexOf("},",index));
			}
			
			ArrayList<String> new_vtxs = new ArrayList<String>();
			String new_vtx = "";
			String temp = "";
			for(int i=0; i< starts.size(); ++i) {
				temp = segment.substring(starts.get(i),ends.get(i)); //get each vtx
				System.out.println(temp);
				String[] numbers = temp.split("[\\{\\}\\s,]+");
				int a = Integer.parseInt(numbers[1]),
					b = Integer.parseInt(numbers[2]),
					c = Integer.parseInt(numbers[3]);
				new_vtx = 	"{{ {" +
							(a + (-min_x)) + ", " +
							b + ", " +
							(c + (-min_z));
				new_vtxs.add(new_vtx);
				System.out.println(a+" "+b+" "+c);
				System.out.println((a+(-min_x))+" "+b+" "+(c+(-min_z)));
			}
			temp = segment.substring(0,starts.get(0));
			for(int i=0; i< starts.size(); ++i) {
				if(i+1 < starts.size()) {
					temp += new_vtxs.get(i) + segment.substring(ends.get(i),starts.get(i+1));
				} else {
					temp += new_vtxs.get(i) + segment.substring(ends.get(i));
				}
			}
			//System.out.println(segment);
			//System.out.println(temp);
			newSegments.add(temp);
		}
		vtxSegments = newSegments;
		
		//update the verts list
		ArrayList<Vertex> new_verts = new ArrayList<Vertex>();
		for(int i=0; i< verts.size(); ++i) {
			Vertex v = verts.get(i);
			new_verts.add(new Vertex(v.x + (-min_x), v.y, v.z + (-min_z)));
		}
		verts = new_verts;
	}
	
	public static void parseFaces() throws IOException {
		tris = new ArrayList<Triangle>();
		for(String segment:triSegments) {
			String[] tri_commands = segment.split("\\),");
			int vert_start_index = 0;
			for(String command : tri_commands) {
				if(command.contains("Vertex")) {
					int start = command.indexOf("gsSPVertex(");
					int end = command.indexOf("+");
					String vert_block_name = command.substring(start+11,end).trim();
					vert_start_index = vertexBlocks.get(vert_block_name);
					
					command = command.substring(end+1);
					vert_start_index += Integer.parseInt(command.substring(0,command.indexOf(",")).trim());
				} else if(command.contains("Triangle")) {
					String[] indices = command.split("[\\\\(\\\\)\\\\s,]+");
					if(command.contains("Triangles")) { //2 tris command
						Vertex 	v1 = verts.get(Integer.parseInt(indices[2].trim()) + vert_start_index),
								v2 = verts.get(Integer.parseInt(indices[3].trim()) + vert_start_index),
								v3 = verts.get(Integer.parseInt(indices[4].trim()) + vert_start_index);
						tris.add(new Triangle(	v1.x,v1.y,v1.z,
												v2.x,v2.y,v2.z,
												v3.x,v3.y,v3.z));
								v1 = verts.get(Integer.parseInt(indices[6].trim()) + vert_start_index);
								v2 = verts.get(Integer.parseInt(indices[7].trim()) + vert_start_index);
								v3 = verts.get(Integer.parseInt(indices[8].trim()) + vert_start_index);
						tris.add(new Triangle(	v1.x,v1.y,v1.z,
												v2.x,v2.y,v2.z,
												v3.x,v3.y,v3.z));
					} else { //1 tri command
						Vertex 	v1 = verts.get(Integer.parseInt(indices[2].trim()) + vert_start_index),
								v2 = verts.get(Integer.parseInt(indices[3].trim()) + vert_start_index),
								v3 = verts.get(Integer.parseInt(indices[4].trim()) + vert_start_index);
						tris.add(new Triangle(	v1.x,v1.y,v1.z,
												v2.x,v2.y,v2.z,
												v3.x,v3.y,v3.z));
					}
				} else {
					continue; //should only get here for end display list command
				}
			}
		}
	}
	
	public static void rebuildFile() throws IOException {
		FileOutputStream fos = new FileOutputStream(dirOut+"model.c");
		String out = "#include <ultra64.h>\n#include \"header.h\"\n";
		for(String s: vtxSegments) out+=s+";\n";
		
		int cumulative_verts = 0;
		int pallette_index_modifier=0;
		for(int i=0; i<materialSegments.size(); ++i) {
			String 	material = materialSegments.get(i),
					tris = triSegments.get(i);
			String triOut = cleanTriBlock(tris, cumulative_verts);
			cumulative_verts = Integer.parseInt(triOut.substring(triOut.indexOf("***")+3));
			triOut= triOut.substring(0,triOut.indexOf("***"));
			//System.out.println(cumulative_verts);
			out+= 	cleanMaterial(material,(6013+i+pallette_index_modifier)) + ";\n" +
					triOut + ";\n";
			
			if(material.contains("ci4_pal")) //materials with a pallette take up 2 indices, so increment the index modifier
				pallette_index_modifier++;
		}
		while(out.contains("gsSPEndDisplayList")) out = out.replace("gsSPEndDisplayList(),", "");
		fos.write(out.getBytes());
		fos.flush();
		fos.close();
	}
	
	public static String cleanMaterial(String mat, int index) {
		if(mat.contains("ci4_pal")) {
			ArrayList<Integer> image_indices = new ArrayList<Integer>();
			for(int i = mat.indexOf(mesh_name); i>=0; i=mat.indexOf(mesh_name, i+1)) {
				image_indices.add(i);
			}
			
			int pallette_start = image_indices.get(1), //pallette is second mesh_name reference after variable name
				pallette_end = mat.indexOf("ci4_pal_rgba16") + 14;
			int	image_start = image_indices.get(2), //image is third mesh_name reference
				image_end = mat.indexOf("_ci4)") + 4;
			
			//pallette index is after image index
			return mat.substring(0,pallette_start)+(index+1)+mat.substring(pallette_end,image_start)+index+mat.substring(image_end);
		} else { //rgba16
			int 	start = mat.lastIndexOf(mesh_name),
					end = mat.indexOf("_rgba16") + 7;
			return mat.substring(0,start)+index+mat.substring(end);
		}
	}
	
	public static String cleanTriBlock(String tris, int verts) {
		String[] vertexDLs = tris.split(mesh_name);
		String ret = "";
		for(int i=1; i<vertexDLs.length; ++i) {
			if(i==1) {
				ret += vertexDLs[0] + mesh_name + vertexDLs[1];
				continue;
			}
			ret += 	"0x0" + Integer.toHexString(0x06000000 + verts * 0x10) +
					vertexDLs[i].substring(vertexDLs[i].indexOf(","));
			String vert_string = vertexDLs[i].substring(vertexDLs[i].indexOf(",")+1);
			vert_string = vert_string.substring(0, vert_string.indexOf(","));
			verts+=Integer.parseInt(vert_string.trim());
			//System.out.println(vert_string+" "+verts);
		}
		return ret+"***"+verts;
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
			System.out.println("Tri "+i+": "+t.x[0]+" "+t.y[0]+" "+t.z[0]+" "
											+t.x[1]+" "+t.y[1]+" "+t.z[1]+" "
											+t.x[2]+" "+t.y[2]+" "+t.z[2]+" ");
			//System.out.println(i);
			t.setFacingAngle();
			if(t.isWall) {
				byte[] wallArray = new BigInteger(String.format("%04x%04x%04x%04x%04x%04x%04x%04x%04x%04x%02xFF0018",t.x[0] & 0xFFFF,t.y[0] & 0xFFFF,t.z[0] & 0xFFFF, 
						t.x[1] & 0xFFFF,t.y[1] & 0xFFFF,t.z[1] & 0xFFFF, 
						t.x[2] & 0xFFFF,t.y[2] & 0xFFFF,t.z[2] & 0xFFFF,
						t.facingAngle,t.directionBit),16).toByteArray();
				if(t.x[0] < 1) {//special case where string format adds leading 0 to first negative number.. so chop it off
					wallArray = new BigInteger(String.format("%04x%04x%04x%04x%04x%04x%04x%04x%04x%04x%02xFF0018",t.x[0] & 0xFFFF,t.y[0] & 0xFFFF,t.z[0] & 0xFFFF, 
						t.x[1] & 0xFFFF,t.y[1] & 0xFFFF,t.z[1] & 0xFFFF, 
						t.x[2] & 0xFFFF,t.y[2] & 0xFFFF,t.z[2] & 0xFFFF,
						t.facingAngle,t.directionBit),16).toByteArray();
					
					byte[] tmp = new byte[wallArray.length-1];
					for(int k=1; k<wallArray.length; ++k) tmp[k-1] = wallArray[k];
					wallArray = tmp;
				}
				for(int j=0; j<(24 - wallArray.length); ++j) wallTriBytes.add((byte)0); //pad if leading 00s get truncated
				for(byte b: wallArray) {
					wallTriBytes.add(b);
					System.out.printf("%02x",b);
				}
				System.out.println();
				System.out.println(String.format("w %04x%04x%04x%04x%04x%04x%04x%04x%04x%04x%02xFF0018",t.x[0] & 0xFFFF,t.y[0] & 0xFFFF,t.z[0] & 0xFFFF, 
						t.x[1] & 0xFFFF,t.y[1] & 0xFFFF,t.z[1] & 0xFFFF, 
						t.x[2] & 0xFFFF,t.y[2] & 0xFFFF,t.z[2] & 0xFFFF,
						t.facingAngle,t.directionBit));
				numWallTris++;
				if(wallTriBytes.size() % 2 != 0) System.out.println("bad");
			}
			byte[] floorArray = new BigInteger(String.format("%04x%04x%04x%04x%04x%04x%04x%04x%04x000001000F70",(t.x[0]*6) & 0xFFFF,(t.x[1]*6) & 0xFFFF,(t.x[2]*6) & 0xFFFF, 
					(t.y[0]*6) & 0xFFFF,(t.y[1]*6) & 0xFFFF,(t.y[2]*6) & 0xFFFF, 
					(t.z[0]*6) & 0xFFFF,(t.z[1]*6) & 0xFFFF,(t.z[2]*6) & 0xFFFF),16).toByteArray();
			
			if(t.x[0] < 0) {//special case where string format adds leading 0 to first negative number.. so chop it off
				floorArray = new BigInteger(String.format("%04x%04x%04x%04x%04x%04x%04x%04x%04x000001000F70",(t.x[0]*6) & 0xFFFF,(t.x[1]*6) & 0xFFFF,(t.x[2]*6) & 0xFFFF, 
						(t.y[0]*6) & 0xFFFF,(t.y[1]*6) & 0xFFFF,(t.y[2]*6) & 0xFFFF, 
						(t.z[0]*6) & 0xFFFF,(t.z[1]*6) & 0xFFFF,(t.z[2]*6) & 0xFFFF),16).toByteArray();
				byte[] tmp = new byte[floorArray.length-1];
				for(int k=1; k<floorArray.length; ++k) tmp[k-1] = floorArray[k];
				floorArray = tmp;
			}
			for(int j=0; j<(24 - floorArray.length); ++j) floorTriBytes.add((byte)0); //pad if leading 00s get truncated
			for(byte b: floorArray) {
				floorTriBytes.add(b);
				System.out.printf("%02x",b);
			}
			System.out.println();
			System.out.println(String.format("f %04x%04x%04x%04x%04x%04x%04x%04x%04x000001000F70",t.x[0]*6 & 0xFFFF,t.x[1]*6 & 0xFFFF,t.x[2]*6 & 0xFFFF, 
					t.y[0]*6 & 0xFFFF,t.y[1]*6 & 0xFFFF,t.y[2]*6 & 0xFFFF, 
					t.z[0]*6 & 0xFFFF,t.z[1]*6 & 0xFFFF,t.z[2]*6 & 0xFFFF));
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
		//System.out.println(cp);
		Vector3d norm = new Vector3d(cp.x,cp.y,cp.z);
		norm.normalize();
		
		if(Math.toDegrees(norm.angle(new Vector3d(0,1,0))) < 125.0 &&
				Math.toDegrees(norm.angle(new Vector3d(0,1,0))) > 55.0) {
			isWall = true;
		}
		//System.out.println(norm);
		//System.out.println(Math.toDegrees(Math.atan2(norm.x, norm.z)));
		double angle = (Math.toDegrees(Math.atan2(norm.x,norm.z)) + 360) % 360;
		directionBit = 1;
		
		//System.out.println("Angle: "+angle);
		int DK64Angle = (int)(angle/360 * 4096);
		//System.out.println(isWall+"\n"+angle);
		//System.out.println(DK64Angle);
		facingAngle = DK64Angle;
	}
}
