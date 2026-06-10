import trimesh
import numpy as np

letters = {
    'P': [
        "### ",
        "#  #",
        "### ",
        "#   ",
        "#   "
    ],
    'A': [
        " ## ",
        "#  #",
        "####",
        "#  #",
        "#  #"
    ],
    ' ': [
        "   ",
        "   ",
        "   ",
        "   ",
        "   "
    ],
    'T': [
        "###",
        " # ",
        " # ",
        " # ",
        " # "
    ],
    'E': [
        "###",
        "#  ",
        "## ",
        "#  ",
        "###"
    ],
    'S': [
        "###",
        "#  ",
        "###",
        "  #",
        "###"
    ]
}

def create_voxel_mesh(text):
    meshes = []
    x_offset = 0
    for char in text:
        grid = letters.get(char, letters[' '])
        width = len(grid[0])
        for y, row in enumerate(grid):
            for x, cell in enumerate(row):
                if cell == '#':
                    box = trimesh.creation.box(extents=(1, 1, 2))
                    box.apply_translation((x_offset + x, 4 - y, 1))
                    meshes.append(box)
        x_offset += width + 1

    scene = trimesh.Scene(meshes)
    final = trimesh.util.concatenate(tuple(scene.geometry.values()))
    # Center it
    final.vertices -= final.center_mass
    final.export('app/src/main/assets/models/pa_test.stl')

create_voxel_mesh('PA TEST')
