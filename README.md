# DK64MapGenerator

To generate a map file and floor file compatible with DK64:  
  
1.  Set the mesh's path in the MapGenerator fileName variable  
2.  Set your output directory in the MapGenerator dirOut variable  
3.  Compile and run MapGenerator (Probably need Java 7 or higher)  
  
Mesh files to be imported are most compatible with the .obj format  
Example:  
vertices,,  
650,51,780  
669,1,780  
664,41,746  
...  
980,60,537  
976,60,539  
faces,,  
material_0,,  
1,2,3  
4,5,6  
5,7,6  
...  
5,17,18  
,,  
material_1,,  
19,20,21  
22,19,21  
,,  
material_2,,  
23,24,25  
25,26,23  
...  
(end of file)  
  
P.S. material_n line entries generate a random color for the FA: G_SETPRIMCOLOR commands.  I suggest you at least include one or else it will not behave correctly, and more than one for contrast purposes.  
  
P.P.S. walls collision isn't quite working yet, but it is suggested that you use the walls file regardless so that you don't have to modify the wall file size entry in the header.  
