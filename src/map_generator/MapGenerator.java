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
	public static String fileName = "E:\\dev\\banjo-kongzooie redux\\blender\\banjos_house\\banjos_house\\model.c";
	public static String dirOut = "E:\\dev\\banjo-kongzooie redux\\blender\\banjos_house\\banjos_house\\output\\";
	
	public static ArrayList<Vertex> verts;
	public static ArrayList<Triangle> tris;
	
	public static byte[] 	geometryHeader,
							geometryVertices,
							geometryF3DEX2,
							geometryFooter;
	
	public static String floorSize, wallSize;
	
	public static int f3dex2Size;
	
	public static boolean water_exists = false;
	
	//collision stuff
	public static int gridSizeX = 400, gridSizeZ = 400;
	public static int gridRows = -1, gridColumns = -1;
	public static int gridMinX = Integer.MAX_VALUE, gridMaxX = Integer.MIN_VALUE;
	public static int gridMinZ = Integer.MAX_VALUE, gridMaxZ = Integer.MIN_VALUE;
	public static ArrayList<ArrayList<Byte>> gridEntryWallBytes, gridEntryFloorBytes;
	
	//blender stuff
	public static ArrayList<String> vtxSegments,
									triSegments,
									materialSegments,
									imageSegments;
	
	public static Map<String,Integer> vertexBlocks;
	
	public static String mesh_name;
	
	//first unused index in texture table = 6013
	//if creating multiple maps, increment by number of textures already used to prevent collisions
	public static int texture_index = 6013;
	
	public static void main(String[] args) throws IOException, FileNotFoundException {
		fileName = args[0].replaceAll("\"", "");
		dirOut = args[1].replaceAll("\"", "")+"\\";
		mesh_name = args[2].replaceAll("\"", "");
		water_exists = args[3].replaceAll("\"", "").equalsIgnoreCase("true") ? true : false;
		texture_index = Integer.parseInt(args[4].replaceAll("\"", ""));
		/*fileName = "C:\\Projects\\DK64\\dk64-hack-base\\blender\\floor_test\\model.c";
		dirOut = "C:\\Projects\\DK64\\dk64-hack-base\\blender\\floor_test\\output\\";
		mesh_name = "floor_test";
		water_exists = false;
		texture_index = 6013;*/
		File file = new File(fileName);
		System.out.println("Parsing model file.");
		parseGfx(file);
		System.out.println("Parsing images.");
		parseImages();
		System.out.println("Parsing vertices.");
		parseVertices();
		System.out.println("Translating vertices to positive XZ.");
		translateVerticesToPositiveXYZ();
		System.out.println("Parsing triangle faces.");
		parseFaces();
		System.out.println("Generating collisions.");
		generateFloorAndWallFiles();
		System.out.println("Building output files.");
		rebuildFile();
		System.out.println("DK64 Map Generator: done.");
	}

	public static void parseGfx(File f) throws IOException {
		Scanner scan = new Scanner(f);
		String whole_file = "";
		while(scan.hasNextLine()) {
			whole_file+=scan.nextLine();
		}
		String[] segments = whole_file.split(";");
		
		vtxSegments = new ArrayList<String>();
		triSegments = new ArrayList<String>();
		materialSegments = new ArrayList<String>();
		imageSegments = new ArrayList<String>();
		for(String s: segments) {
			if(s.contains("cull") || s.contains("Cull")) continue;
			if(s.contains("Gfx")) {
				//if(s.contains("revert") || s.contains("Revert")) continue;
				if(s.contains("tri_")) {
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
		if(null_index < 0) return;
		materialSegments.remove(null_index);
		vtxSegments.remove(null_index);
		triSegments.remove(null_index);
	}
	
	public static void parseImages() throws IOException {
		Files.createDirectories(Paths.get(dirOut+"/textures"));
		Files.createDirectories(Paths.get(dirOut+"/textures/"+mesh_name));
		
		FileOutputStream fos = new FileOutputStream(dirOut+"build_imports.txt");
		String out = "";
		
		int build_file_texture_index = texture_index;
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
						"\t\t\"file_index\": "+(build_file_texture_index++)+",\n" +
						"\t\t\"source_file\": \"bin/"+mesh_name+"/"+s.substring(start,end)+name+".bin\",\n" +
						"\t\t\"do_not_extract\": True\n" +
					  "\t},\n";
				
				exportImage(s, s.substring(start,end)+name);
			} else { //rgba16
				String codec = "";
				if(s.contains("rgba32")) codec = "rgba32";
				else if(s.contains("i8")) codec = "i8";
				else if(s.contains("i16")) codec = "i16";
				else codec = "rgba16";
				int 	start = s.indexOf(mesh_name) + mesh_name.length() + 1,
						end = 	s.indexOf("_"+codec);
				while(s.substring(start).startsWith("_")) start++;
				
				out+= "\t{\n"+
						"\t\t\"name\": \""+s.substring(start,end)+"\",\n" +
						"\t\t\"pointer_table_index\": 25,\n" +
						"\t\t\"file_index\": "+(build_file_texture_index++)+",\n" +
						"\t\t\"source_file\": \"bin/"+mesh_name+"/"+s.substring(start,end)+".bin\",\n" +
						"\t\t\"do_not_extract\": True\n" +
					  "\t},\n";
				
				exportImage(s, s.substring(start,end));
			}
		}
		fos.write(out.getBytes());
		fos.flush();
		fos.close();
	}
	
	public static void exportImage(String imageSegment, String name) throws IOException{
		FileOutputStream fos = new FileOutputStream(dirOut+"textures\\"+mesh_name+"\\"+name+".bin");
		
		String[] imageBytes = imageSegment.split("[{},]");

		int bytes = 0;
		for(int i=1; i<imageBytes.length; ++i) {
			if(!imageBytes[i].contains("0x")) continue;
			String hex = imageBytes[i].substring(imageBytes[i].indexOf("0x")+2).trim();
			byte[] bArray = new byte[8];
			for(int j=0; j<hex.length(); j+=2) {
				bArray[j/2] = (byte) ((Character.digit(hex.charAt(j), 16) << 4) + Character.digit(hex.charAt(j+1), 16));
			}
			fos.write(bArray);
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
			////System.out.println(segment.substring(start,end)+" "+cumulative_vertices);
			
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
		
		//remove vertex color data until we figure that out, use vertex lighting and set to full brightness
		for(int i=0; i<vtxSegments.size(); ++i) {
			String segment = vtxSegments.get(i);
			String new_segment = "";
			while(segment.contains(", {")) {
				new_segment += segment.substring(0,segment.indexOf(", {")+3);
				segment = segment.substring(segment.indexOf(", {")+3);
				new_segment += segment.substring(0,segment.indexOf(", {")+3);
				segment = segment.substring(segment.indexOf(", {")+3);
				
				//System.out.println(segment.substring(0,segment.indexOf("} }},")));
				//new_segment += "255, 255, 255, 255} }},";
				new_segment += segment.substring(0,segment.indexOf("} }},")+5);
				segment = segment.substring(segment.indexOf("} }},")+5);
			}
			new_segment += segment;
			//System.out.println(new_segment);
			vtxSegments.set(i,new_segment);
		}
	}

	public static void translateVerticesToPositiveXYZ() throws IOException {
		int min_x=Integer.MAX_VALUE, min_y = Integer.MAX_VALUE, min_z=Integer.MAX_VALUE;
		for(Vertex v: verts) {
			if(v.x < min_x) min_x = v.x;
			if(v.y < min_y) min_y = v.y;
			if(v.z < min_z) min_z = v.z;
		}
		if(min_x >= 0 && min_y >= 0 && min_z >= 0) return; //already +XZ
		//System.out.println("MINS: "+min_x+" "+min_y+" "+min_z);
		
		//update the vert blocks in the model.c file
		ArrayList<String> newSegments = new ArrayList<String>();
		for(String segment: vtxSegments) {
			ArrayList<Integer> 	starts = new ArrayList<Integer>(),
								ends = new ArrayList<Integer>();
			for(int index = segment.indexOf("{{ {"); index >= 0; index = segment.indexOf("{{ {", index+1)) {
				starts.add(index);
				ends.add(segment.indexOf("},",index));
			}
			
			ArrayList<String> new_vtxs = new ArrayList<String>();
			String new_vtx = "";
			String temp = "";
			
			int water_multiplier = water_exists ? 3 : 1; //no idea why but with chunks/water planes *3 the visual vertices
			for(int i=0; i< starts.size(); ++i) {
				temp = segment.substring(starts.get(i),ends.get(i)); //get each vtx

				String[] numbers = temp.split("[\\{\\}\\s,]+");
				int a = Integer.parseInt(numbers[1]),
					b = Integer.parseInt(numbers[2]),
					c = Integer.parseInt(numbers[3]);
				new_vtx = 	"{{ {" +
							(a + (-min_x))*water_multiplier + ", " +
							(b + (-min_y))*water_multiplier + ", " +
							(c + (-min_z))*water_multiplier;
				new_vtxs.add(new_vtx);
			}
			temp = segment.substring(0,starts.get(0));
			for(int i=0; i< starts.size(); ++i) {
				if(i+1 < starts.size()) {
					temp += new_vtxs.get(i) + segment.substring(ends.get(i),starts.get(i+1));
				} else {
					temp += new_vtxs.get(i) + segment.substring(ends.get(i));
				}
			}

			newSegments.add(temp);
		}
		vtxSegments = newSegments;
		
		//update the verts list
		ArrayList<Vertex> new_verts = new ArrayList<Vertex>();
		for(int i=0; i< verts.size(); ++i) {
			Vertex v = verts.get(i);
			Vertex vn = new Vertex(v.x + (-min_x), v.y + (-min_y), v.z + (-min_z));
			
			new_verts.add(vn);
			
			gridMinX = vn.x < gridMinX ? vn.x : gridMinX;
			gridMinZ = vn.z < gridMinZ ? vn.z : gridMinZ;
			gridMaxX = vn.x > gridMaxX ? vn.x : gridMaxX;
			gridMaxZ = vn.z > gridMaxZ ? vn.z : gridMaxZ;
		}
		verts = new_verts;
		gridColumns = (int)(Math.ceil(((double)gridMaxX) / gridSizeX));
		gridRows = (int)(Math.ceil(((double)gridMaxZ) / gridSizeZ));
		
		System.out.println("MINS: "+gridMinX+" "+gridMinZ);
		System.out.println("MAXS: "+gridMaxX+" "+gridMaxZ);
		System.out.println("ROWS: "+gridRows+" COLUMNS: "+gridColumns);
	}
	
	public static void parseFaces() throws IOException {
		tris = new ArrayList<Triangle>();
		int material_counter = 0;
		for(String segment:triSegments) {
			//Getting floor properties from material name
			String material_segment = materialSegments.get(material_counter);
			//System.out.println(material_segment.substring(0,material_segment.indexOf("[]")));
			int 	prop_void = 0, 
					prop_floor_type = 0, 
					sfx = 0;
			if(material_segment.contains("PROP_VOID")) {
				prop_void = 0x40;
			} else if(material_segment.contains("PROP_SAND")) {
				prop_floor_type = 0x40;
			} else if(material_segment.contains("PROP_WATER")) {
				prop_floor_type = 0x01;
			} else if(material_segment.contains("PROP_DEATH")) {
				prop_floor_type = 0x10;
			} else if(material_segment.contains("SFX_WOOD")) {
				sfx = 0x02;
			} else if(material_segment.contains("SFX_METAL")) {
				sfx = 0x08;
			} else if(material_segment.contains("SFX_LEAVES")) {
				sfx = 0x01;
			}
			material_counter += 2;
			
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
												v3.x,v3.y,v3.z,
												prop_void, prop_floor_type, sfx));
								v1 = verts.get(Integer.parseInt(indices[6].trim()) + vert_start_index);
								v2 = verts.get(Integer.parseInt(indices[7].trim()) + vert_start_index);
								v3 = verts.get(Integer.parseInt(indices[8].trim()) + vert_start_index);
						tris.add(new Triangle(	v1.x,v1.y,v1.z,
												v2.x,v2.y,v2.z,
												v3.x,v3.y,v3.z,
												prop_void, prop_floor_type, sfx));
					} else { //1 tri command
						Vertex 	v1 = verts.get(Integer.parseInt(indices[2].trim()) + vert_start_index),
								v2 = verts.get(Integer.parseInt(indices[3].trim()) + vert_start_index),
								v3 = verts.get(Integer.parseInt(indices[4].trim()) + vert_start_index);
						tris.add(new Triangle(	v1.x,v1.y,v1.z,
												v2.x,v2.y,v2.z,
												v3.x,v3.y,v3.z,
												prop_void, prop_floor_type, sfx));
					}
				} else {
					continue; //should only get here for end display list command
				}
			}
		}
		
		HashMap<Integer,Integer> map = new HashMap<Integer,Integer>();
		for(Triangle face: tris) {
			face.setGridNumbers(findGridNumbers(face));
			for(int k : face.gridNumbers) {
				if(!map.containsKey(k)) {
					map.put(k, 1);
				} else {
					map.put(k, map.get(k)+1);
				}
			}
		}
		for(int i=0; i<gridRows; ++i) {
			for(int j=0; j<gridColumns; ++j) {
				//System.out.printf("%5d",map.get(i*gridColumns+j));
			}
			//System.out.println();
		}
	}
	
	/*
	 *April 14, 2024 
	 *Found bug that its not sufficient to put collisions only into grids where vertices lay.
	 *Fixed so that it adds grid numbers for the entire rectangle that the tri's verts span on the grid.
	 */
	public static ArrayList<Integer> findGridNumbers(Triangle face) {
		ArrayList<Integer> gn = new ArrayList<Integer>();
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for(int i=0; i<3; i++) {
			int 	x = face.x[i],
					z = face.z[i];
			if(x > maxX) maxX = x;
			if(x < minX) minX = x;
			if(z > maxZ) maxZ = z;
			if(z < minZ) minZ = z;
		}
		/*if(minX == 799 || minX==774 || minX==660 || maxX == 799 || maxX==774 || maxX==660 )
			System.out.println("GRID NUMBERS");
		else
			return gn;*/
		
		//int gridNumber =  (z / gridSizeZ)*gridColumns + (x / gridSizeX);
		int TL = (minZ / gridSizeZ)*gridColumns + (minX / gridSizeX),
			TR = (minZ / gridSizeZ)*gridColumns + (maxX / gridSizeX),
			BL = (maxZ / gridSizeZ)*gridColumns + (minX / gridSizeX),
			BR = (maxZ / gridSizeZ)*gridColumns + (maxX / gridSizeX);
		//System.out.println("TL: "+TL+" TR: "+TR+" BL: "+BL+" BR: "+BR);
		for(int i = 0; i<=(BL - TL)/gridColumns; ++i) {
			for(int j=0; j<=(TR - TL); ++j) {
				int gridNumber = TL + i*gridColumns + j;
				if(!gn.contains(gridNumber)) gn.add(gridNumber);
				//System.out.println("GRID NUMBER: "+gridNumber);
			}
		}
		
		//if(!gn.contains(gridNumber)) gn.add(gridNumber);
		//if(x == 799 || x==774 || x==660)
		//	System.out.println("GRID NUMBER: "+gridNumber+" "+x+" "+z);
		//System.out.println();
		return gn;
	}
	
	public static void rebuildFile() throws IOException {
		FileOutputStream fos = new FileOutputStream(dirOut+"model.c");
		String out = "#include <ultra64.h>\n#include \"header.h\"\n";
		for(String s: vtxSegments) out+=s+";\n";
		
		int cumulative_verts = 0;
		int pallette_index_modifier=0;
		for(int i=0; i<materialSegments.size(); i+=2) {
			//2 material blocks for every tri block
			//material -> tris -> material revert
			String 	material = materialSegments.get(i),
					tris = triSegments.get(i/2),
					material_revert = materialSegments.get(i+1);
			String triOut = cleanTriBlock(tris, cumulative_verts);
			cumulative_verts = Integer.parseInt(triOut.substring(triOut.indexOf("***")+3));
			triOut= triOut.substring(0,triOut.indexOf("***"));
			////System.out.println(cumulative_verts);
			out+= 	cleanMaterial(material,(texture_index+(i/2)+pallette_index_modifier)) + ";\n" +
					triOut + ";\n"+
					material_revert + ";\n";
			
			if(!material.contains("revert") && material.contains("ci4_pal")) //materials with a pallette take up 2 indices, so increment the index modifier
				pallette_index_modifier++;
		}
		while(out.contains("gsSPEndDisplayList")) out = out.replace("gsSPEndDisplayList(),", "");
		//while(out.contains("gsSPGeometryMode(0, G_FOG)")) out = out.replace("gsSPGeometryMode(0, G_FOG),", "");
		//G_RM_FOG_SHADE_A,
		//while(out.contains("G_RM_FOG_SHADE_A,")) out = out.replace("G_RM_FOG_SHADE_A,","G_RM_AA_ZB_OPA_SURF,");
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
			
			////System.out.println(mat);
			int pallette_start = image_indices.get(1), //pallette is second mesh_name reference after variable name
				pallette_end = mat.indexOf("ci4_pal_rgba16") + 14;
			int	image_start = image_indices.get(2), //image is third mesh_name reference
				image_end = mat.indexOf("_ci4)") + 4;
			
			//pallette index is after image index
			return mat.substring(0,pallette_start)+(index+1)+mat.substring(pallette_end,image_start)+index+mat.substring(image_end);
		} else { //rgba16
			String codec = "";
			if(mat.contains("_rgba32")) codec = "rgba32";
			else if(mat.contains("_i8")) codec = "i8";
			else if(mat.contains("_i16")) codec = "i16";
			else codec = "rgba16";
			int 	start = mat.lastIndexOf(mesh_name),
					end = mat.indexOf("_"+codec) + (codec.length() + 1);
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
			////System.out.println(vert_string+" "+verts);
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
			////System.out.println(String.format("%04x%04x%04x000000000000000000FF",Integer.parseInt(line[0]), 
			//																				Integer.parseInt(line[1]),
			//																				Integer.parseInt(line[2])));
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
				////System.out.println("********"+"FA000000"+color);
				continue;
			}
			
			////System.out.println("Face "+(index++)+" buffer chunk: "+buffer_chunk);
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
						////System.out.println("***"+"0102004006"+segmented_addr);
					}
			} else {
				String segmented_addr1 = String.format("%06x", Integer.parseInt(line[0])*16),
						segmented_addr2 = String.format("%06x", Integer.parseInt(line[1])*16),
						segmented_addr3 = String.format("%06x", Integer.parseInt(line[2])*16);
				vertArray = new BigInteger(	"0100100206"+segmented_addr1+
											"0100100406"+segmented_addr2+
											"0100100606"+segmented_addr3,16).toByteArray(); //start loading vertices at index 0
				for(byte b: vertArray) byteList.add(b);
				////System.out.println(	"***0100100206"+segmented_addr1+
				//					"***0100100406"+segmented_addr2+
				//					"***0100100606"+segmented_addr3);
				
				//add simplified G_TRI command
				byte[] bArray = new BigInteger("0500020400000000",16).toByteArray();
				////System.out.println("0500020400000000");
				Vertex 	v1 = verts.get(Integer.parseInt(line[0])),
						v2 = verts.get(Integer.parseInt(line[1])),
						v3 = verts.get(Integer.parseInt(line[2]));
				triData.add(new Triangle(	v1.x, v1.y, v1.z,
											v2.x, v2.y, v2.z,
											v3.x, v3.y, v3.z,0,0,0));
				for(int i=0; i<(8 - bArray.length); ++i) byteList.add((byte)0); //pad if leading 00s get truncated
				for(byte b: bArray)
					byteList.add(b);
				
				buffer_chunk = -1; //reset buffer_chunk so it will get a load vertex instruction
				continue;
			}
			
			byte[] bArray = new BigInteger(String.format("05%02x%02x%02x00000000",(Integer.parseInt(line[0])*2)%64, 
																				  (Integer.parseInt(line[1])*2)%64,
																				  (Integer.parseInt(line[2])*2)%64),16).toByteArray();
			////System.out.println(String.format("05%02x%02x%02x00000000",(Integer.parseInt(line[0])*2)%64, 
			//														  (Integer.parseInt(line[1])*2)%64,
			//														  (Integer.parseInt(line[2])*2)%64));
			//points.add(new Vertex(Integer.parseInt(line[0]), Integer.parseInt(line[1]), Integer.parseInt(line[2])));
			Vertex 	v1 = verts.get(Integer.parseInt(line[0])),
					v2 = verts.get(Integer.parseInt(line[1])),
					v3 = verts.get(Integer.parseInt(line[2]));
			triData.add(new Triangle(v1.x, v1.y, v1.z,
									 v2.x, v2.y, v2.z,
									 v3.x, v3.y, v3.z,0,0,0));
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
		int numWallTris = 0,
			numFloorTris = 0;
		
		gridEntryWallBytes = new ArrayList<ArrayList<Byte>>();
		gridEntryFloorBytes = new ArrayList<ArrayList<Byte>>();
		for(int i=0; i<gridRows*gridColumns; ++i) {
			gridEntryWallBytes.add(new ArrayList<Byte>());
			gridEntryFloorBytes.add(new ArrayList<Byte>());
		}
		
		for(int i=0; i<tris.size(); ++i) {
			ArrayList<Byte> floorTriBytes = new ArrayList<>(),
							wallTriBytes = new ArrayList<>();
			
			Triangle t = tris.get(i);
			t.setFacingAngle();
			if(t.isWall) {
				byte[] x0 = new BigInteger(String.format("%04x",t.x[0] & 0xFFFF),16).toByteArray();
				if(x0.length < 2) {
					x0 = new byte[]{0,x0[0]};
				} else if(x0.length > 2) {
					x0 = new byte[]{x0[1],x0[2]};
				}
				byte[] y0 = new BigInteger(String.format("%04x",t.y[0] & 0xFFFF),16).toByteArray();
				if(y0.length < 2) {
					y0 = new byte[]{0,y0[0]};
				} else if(y0.length > 2) {
					y0 = new byte[]{y0[1],y0[2]};
				}
				byte[] z0 = new BigInteger(String.format("%04x",t.z[0] & 0xFFFF),16).toByteArray();
				if(z0.length < 2) {
					z0 = new byte[]{0,z0[0]};
				} else if(z0.length > 2) {
					z0 = new byte[]{z0[1],z0[2]};
				}
				
				byte[] x1 = new BigInteger(String.format("%04x",t.x[1] & 0xFFFF),16).toByteArray();
				if(x1.length < 2) {
					x1 = new byte[]{0,x1[0]};
				} else if(x1.length > 2) {
					x1 = new byte[]{x1[1],x1[2]};
				}
				byte[] y1 = new BigInteger(String.format("%04x",t.y[1] & 0xFFFF),16).toByteArray();
				if(y1.length < 2) {
					y1 = new byte[]{0,y1[0]};
				} else if(y1.length > 2) {
					y1 = new byte[]{y1[1],y1[2]};
				}
				byte[] z1 = new BigInteger(String.format("%04x",t.z[1] & 0xFFFF),16).toByteArray();
				if(z1.length < 2) {
					z1 = new byte[]{0,z1[0]};
				} else if(z1.length > 2) {
					z1 = new byte[]{z1[1],z1[2]};
				}
				
				byte[] x2 = new BigInteger(String.format("%04x",t.x[2] & 0xFFFF),16).toByteArray();
				if(x2.length < 2) {
					x2 = new byte[]{0,x2[0]};
				} else if(x2.length > 2) {
					x2 = new byte[]{x2[1],x2[2]};
				}
				byte[] y2 = new BigInteger(String.format("%04x",t.y[2] & 0xFFFF),16).toByteArray();
				if(y2.length < 2) {
					y2 = new byte[]{0,y2[0]};
				} else if(y2.length > 2) {
					y2 = new byte[]{y2[1],y2[2]};
				}
				byte[] z2 = new BigInteger(String.format("%04x",t.z[2] & 0xFFFF),16).toByteArray();
				if(z2.length < 2) {
					z2 = new byte[]{0,z2[0]};
				} else if(z2.length > 2) {
					z2 = new byte[]{z2[1],z2[2]};
				}
				
				byte[] facingAngle = new BigInteger(String.format("%04x",t.facingAngle),16).toByteArray();
				if(facingAngle.length < 2) {
					facingAngle = new byte[]{0,facingAngle[0]};
				} else if(facingAngle.length > 2) {
					facingAngle = new byte[]{facingAngle[1],facingAngle[2]};
				}
				byte[] directionBit = new byte[] {(byte) t.directionBit};
				byte[] footer =  new byte[] {(byte) 0xFF, 0x00, 0x18};
				
				if((x0.length != 2) || (y0.length != 2) || (z0.length != 2) ||
						(x1.length != 2) ||(y1.length != 2) ||(z1.length != 2) ||
						(x2.length != 2) ||(y2.length != 2) ||(z2.length != 2) ||
						(facingAngle.length != 2) || (directionBit.length != 1) ||(footer.length != 3)) ;//System.out.println("********BAD BYTE ARRAY");
				
				byte[] wallArray = new byte[] {x0[0], x0[1], y0[0], y0[1], z0[0], z0[1],
										x1[0], x1[1], y1[0], y1[1], z1[0], z1[1],
										x2[0], x2[1], y2[0], y2[1], z2[0], z2[1],
										facingAngle[0], facingAngle[1], directionBit[0],
										footer[0], footer[1], footer[2]
										};
				
				//for each grid the tri is in, add its collision to the list (can be dupes)
				for(int k : t.gridNumbers) {
					for(byte b: wallArray) {
						gridEntryWallBytes.get(k).add(b);
					}
				}
				
				numWallTris++;
			}
			if(t.isFloor) { 
				byte[] x0 = new BigInteger(String.format("%04x",t.x[0]*6 & 0xFFFF),16).toByteArray();
				if(x0.length < 2) {
					x0 = new byte[]{0,x0[0]};
				} else if(x0.length > 2) {
					x0 = new byte[]{x0[1],x0[2]};
				}
				byte[] y0 = new BigInteger(String.format("%04x",t.y[0]*6 & 0xFFFF),16).toByteArray();
				if(y0.length < 2) {
					y0 = new byte[]{0,y0[0]};
				} else if(y0.length > 2) {
					y0 = new byte[]{y0[1],y0[2]};
				}
				byte[] z0 = new BigInteger(String.format("%04x",t.z[0]*6 & 0xFFFF),16).toByteArray();
				if(z0.length < 2) {
					z0 = new byte[]{0,z0[0]};
				} else if(z0.length > 2) {
					z0 = new byte[]{z0[1],z0[2]};
				}
				
				byte[] x1 = new BigInteger(String.format("%04x",t.x[1]*6 & 0xFFFF),16).toByteArray();
				if(x1.length < 2) {
					x1 = new byte[]{0,x1[0]};
				} else if(x1.length > 2) {
					x1 = new byte[]{x1[1],x1[2]};
				}
				byte[] y1 = new BigInteger(String.format("%04x",t.y[1]*6 & 0xFFFF),16).toByteArray();
				if(y1.length < 2) {
					y1 = new byte[]{0,y1[0]};
				} else if(y1.length > 2) {
					y1 = new byte[]{y1[1],y1[2]};
				}
				byte[] z1 = new BigInteger(String.format("%04x",t.z[1]*6 & 0xFFFF),16).toByteArray();
				if(z1.length < 2) {
					z1 = new byte[]{0,z1[0]};
				} else if(z1.length > 2) {
					z1 = new byte[]{z1[1],z1[2]};
				}
				
				byte[] x2 = new BigInteger(String.format("%04x",t.x[2]*6 & 0xFFFF),16).toByteArray();
				if(x2.length < 2) {
					x2 = new byte[]{0,x2[0]};
				} else if(x2.length > 2) {
					x2 = new byte[]{x2[1],x2[2]};
				}
				byte[] y2 = new BigInteger(String.format("%04x",t.y[2]*6 & 0xFFFF),16).toByteArray();
				if(y2.length < 2) {
					y2 = new byte[]{0,y2[0]};
				} else if(y2.length > 2) {
					y2 = new byte[]{y2[1],y2[2]};
				}
				byte[] z2 = new BigInteger(String.format("%04x",t.z[2]*6 & 0xFFFF),16).toByteArray();
				if(z2.length < 2) {
					z2 = new byte[]{0,z2[0]};
				} else if(z2.length > 2) {
					z2 = new byte[]{z2[1],z2[2]};
				}
				
				byte 	prop_void = (byte)t.prop_void,
						prop_floor_type = (byte)t.prop_floor_type,
						sfx = (byte)t.sfx;
				byte[] footer =  new byte[] {prop_void,prop_floor_type,0x01,sfx,0x0F,0x70};
				
				if((x0.length != 2) || (y0.length != 2) || (z0.length != 2) ||
						(x1.length != 2) ||(y1.length != 2) ||(z1.length != 2) ||
						(x2.length != 2) ||(y2.length != 2) ||(z2.length != 2) ||
						(footer.length != 6)) ;//System.out.println("********BAD BYTE ARRAY");
				
				
				byte[] floorArray = new byte[] {x0[0], x0[1], x1[0], x1[1], x2[0], x2[1],
						y0[0], y0[1], y1[0], y1[1], y2[0], y2[1],
						z0[0], z0[1], z1[0], z1[1], z2[0], z2[1],
						footer[0], footer[1], footer[2], footer[3], footer[4], footer[5]
						};
	
				//for each grid the tri is in, add its collision to the list (can be dupes)
				for(int k : t.gridNumbers) {
					for(byte b: floorArray) {
						gridEntryFloorBytes.get(k).add(b);
					}
				}
				numFloorTris++;
			}
		}
		floorSize = String.format("%08x", numFloorTris*24+8);
		wallSize = String.format("%08x", numWallTris*24+8);
		
		ArrayList<Byte> floorHeader = new ArrayList<Byte>(),
						wallHeader = new ArrayList<Byte>();
		byte[] floorHeaderBytes = new BigInteger(String.format("%08x",numFloorTris),16).toByteArray(),
				wallHeaderBytes = new BigInteger(String.format("%08x",numWallTris),16).toByteArray();
		
		ArrayList<Byte> floorOutputBytes = new ArrayList<>(),
						wallOutputBytes = new ArrayList<>();
		
		for(int i=0; i<(4 - floorHeaderBytes.length); ++i) floorHeader.add((byte)0); //pad if leading 00s get truncated
		for(byte b: floorHeaderBytes) floorHeader.add(b);
		for(int i=0; i<floorHeader.size(); ++i) floorOutputBytes.add(i,floorHeader.get(i));
		
		for(int i=0; i<(4 - wallHeaderBytes.length); ++i) wallHeader.add((byte)0); //pad if leading 00s get truncated
		for(byte b: wallHeaderBytes) wallHeader.add(b);
		for(int i=0; i<wallHeader.size(); ++i) wallOutputBytes.add(i,wallHeader.get(i));
		
		System.out.println("Tris:"+tris.size()+"\nVerts:"+verts.size());
		System.out.println("Walls: "+numWallTris+"\nFloors: "+numFloorTris);
		
		int ptrToNextBlock = 4;
		for(int i=0; i<gridEntryFloorBytes.size(); ++i) {
			ArrayList<Byte> gridEntryBytes = gridEntryFloorBytes.get(i);
			ptrToNextBlock = floorOutputBytes.size() + gridEntryBytes.size() + 4;
			
			//pad 00s in pointer
			byte[] ptrBytes = new BigInteger(String.format("%08x", ptrToNextBlock),16).toByteArray();
			ArrayList<Byte> ptrByteList = new ArrayList<Byte>();
			for(int j=0; j<(4 - ptrBytes.length); ++j) ptrByteList.add((byte)0);
			for(byte b: ptrBytes) ptrByteList.add(b);
			for(byte b: ptrByteList) floorOutputBytes.add(b);
			
			for(int j=0; j<gridEntryBytes.size(); ++j) {
				floorOutputBytes.add(gridEntryBytes.get(j));
			}
		}
		ptrToNextBlock = 4;
		for(int i=0; i<gridEntryWallBytes.size(); ++i) {
			ArrayList<Byte> gridEntryBytes = gridEntryWallBytes.get(i);
			ptrToNextBlock = wallOutputBytes.size() + gridEntryBytes.size() + 4;
			
			//pad 00s in pointer
			byte[] ptrBytes = new BigInteger(String.format("%08x", ptrToNextBlock),16).toByteArray();
			ArrayList<Byte> ptrByteList = new ArrayList<Byte>();
			for(int j=0; j<(4 - ptrBytes.length); ++j) ptrByteList.add((byte)0);
			for(byte b: ptrBytes) ptrByteList.add(b);
			for(byte b: ptrByteList) wallOutputBytes.add(b);
			
			for(int j=0; j<gridEntryBytes.size(); ++j) {
				wallOutputBytes.add(gridEntryBytes.get(j));
			}
		}
		
		byte[] 	floorData = new byte[floorOutputBytes.size()],
				wallData = new byte[wallOutputBytes.size()];
		for(int i=0; i<floorOutputBytes.size(); ++i) floorData[i] = floorOutputBytes.get(i).byteValue();
		for(int i=0; i<wallOutputBytes.size(); ++i) wallData[i] = wallOutputBytes.get(i).byteValue();
		
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
	boolean isFloor = true;
	int facingAngle = 0;
	int directionBit = 0;
	
	//Floor properties
	int prop_void = 0;
	int prop_floor_type = 0;
	int sfx = 0;
	//int brightness = 255; -- not implemented
	
	ArrayList<Integer> gridNumbers;
	public Triangle(int x1, int y1, int z1,
					int x2, int y2, int z2,
					int x3, int y3, int z3,
					int prop_void, int prop_floor_type, int sfx) {
		x[0] = x1; x[1] = x2; x[2] = x3;
		y[0] = y1; y[1] = y2; y[2] = y3;
		z[0] = z1; z[1] = z2; z[2] = z3;
		this.prop_void = prop_void;
		this.prop_floor_type = prop_floor_type;
		this.sfx = sfx;
	}
	
	public void setFacingAngle() {
		Vector3d v1 = new Vector3d(x[1] - x[0], y[1] - y[0], z[1] - z[0]);
		Vector3d v2 = new Vector3d(x[2] - x[0], y[2] - y[0], z[2] - z[0]);
		Vector3d cp = new Vector3d(); cp.cross(v1, v2);
		////System.out.println(cp);
		Vector3d norm = new Vector3d(cp.x,cp.y,cp.z);
		norm.normalize();
		
		if(Math.toDegrees(norm.angle(new Vector3d(0,1,0))) < 120.0 &&
				Math.toDegrees(norm.angle(new Vector3d(0,1,0))) > 60.0) {
			isWall = true;
			isFloor = false;
		}
		if(Math.toDegrees(norm.angle(new Vector3d(0,1,0))) < 90.0) {
			isFloor = true;
		}
		//System.out.println(Math.toDegrees(norm.angle(new Vector3d(0,1,0))));
		////System.out.println(norm);
		////System.out.println(Math.toDegrees(Math.atan2(norm.x, norm.z)));
		double angle = (Math.toDegrees(Math.atan2(norm.x,norm.z)) + 360) % 360;
		double[] angle_exceptions_list = new double[]{111.5,112,112.5,113,113.5,114,114.5,115,
												117,117.5,118,118.5,119,119.5,120,120.5,121,121.5,122,												
												288,288.5,289,289.5,290,290.5,291,291.5,292,202.5,293,293.5,294,
												329,329.5,330,330.5,331,331.5,332,332.5,333,
												339.5,340,340.5,341,341.5,342,342.5,343,343.5,344,344.5,345};
		if(	angle >= 135 && angle < 340) {
			directionBit = 1;
			angle = angle;
		} else {
			angle = (angle + 180) % 360; //flip 180 degrees
			directionBit = 0;
		}
		//handle list of exceptions where collision gets bad
		for(int i=0; i<angle_exceptions_list.length; ++i) {
			if(Math.abs(angle - angle_exceptions_list[i]) <= 0.25) { //within quarter a degree of the angle exception list entry
				if(directionBit == 0) {
					angle = (angle + 180) % 360; //flip 180 degrees
					directionBit = 1;
					break;
				} else {
					angle = (angle + 180) % 360; //flip 180 degrees
					directionBit = 0;
					break;
				}
			}
		}
		
		////System.out.println("Angle: "+angle);
		int DK64Angle = (int)(angle/360 * 4096);
		////System.out.println(isWall+"\n"+angle);
		////System.out.println(DK64Angle);
		facingAngle = DK64Angle;
	}
	
	public void setGridNumbers(ArrayList<Integer> gn) {
		gridNumbers = gn;
	}
}
