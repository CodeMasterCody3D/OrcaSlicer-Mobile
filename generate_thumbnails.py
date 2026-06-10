import trimesh
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D
import fast_simplification
import glob
import os
import numpy as np

def simplify_mesh(mesh, target_faces=2000):
    if len(mesh.faces) > target_faces:
        points, faces = fast_simplification.simplify(mesh.vertices, mesh.faces, target_faces / len(mesh.faces))
        return trimesh.Trimesh(vertices=points, faces=faces)
    return mesh

for f in glob.glob('app/src/main/assets/models/*.stl'):
    base_name = os.path.basename(f)
    if 'pa_test' in base_name: continue # Skip the PA test placeholder
    
    out_name = 'model_thumb_' + base_name.replace('.stl', '.png').lower()
    out_path = os.path.join('app/src/main/res/drawable', out_name)
    if os.path.exists(out_path): continue
    print("Rendering", out_name)
    
    mesh = trimesh.load(f)
    if hasattr(mesh, 'geometry'):
        mesh = trimesh.util.concatenate(tuple(mesh.geometry.values()))
        
    mesh = simplify_mesh(mesh)
    
    fig = plt.figure(figsize=(2, 2), dpi=150)
    ax = fig.add_subplot(111, projection='3d')
    
    v = mesh.vertices
    ax.plot_trisurf(v[:,0], v[:,1], v[:,2], triangles=mesh.faces, color='#aaaaaa', edgecolor='none')
    
    # Set aspect ratio properly
    extents = np.ptp(v, axis=0)
    max_ext = np.max(extents)
    centers = (np.max(v, axis=0) + np.min(v, axis=0)) / 2
    ax.set_xlim(centers[0] - max_ext/2, centers[0] + max_ext/2)
    ax.set_ylim(centers[1] - max_ext/2, centers[1] + max_ext/2)
    ax.set_zlim(centers[2] - max_ext/2, centers[2] + max_ext/2)
    
    ax.axis('off')
    ax.view_init(elev=30, azim=-45)
    
    plt.savefig(out_path, transparent=True, bbox_inches='tight', pad_inches=0)
    plt.close()
    print("Saved", out_path)
