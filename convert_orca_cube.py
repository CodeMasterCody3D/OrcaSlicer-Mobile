import trimesh
import DracoPy
import os

files = [
    '/home/cody/Downloads/OrcaPlug_v2.drc',
    '/home/cody/Downloads/OrcaCube_v2.drc'
]

for f in files:
    print(f"Converting {f}...")
    try:
        with open(f, 'rb') as fp:
            mesh_data = DracoPy.decode(fp.read())
        mesh = trimesh.Trimesh(vertices=mesh_data.points, faces=mesh_data.faces)
        stl_path = os.path.splitext(f)[0] + '.stl'
        mesh.export(stl_path)
        print(f"Saved {stl_path}")
    except Exception as e:
        print(f"Failed {f}: {e}")
