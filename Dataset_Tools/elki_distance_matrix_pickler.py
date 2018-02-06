"""
Read an ELKI distance matrix and export a pickle file with its data
"""
import csv

import pickle


def main():
    elki_file = "20NG/elki_distance_matrix.txt"
    out_file = "20ng_entity_graph_with_tfidf_NVS.p"

    values = {
        "texts1": [],
        "texts2": [],
        "NVS": []
    }
    # values = {}

    # Read values
    with open(elki_file, "r") as f:
        reader = csv.reader(f, delimiter=" ", quoting=csv.QUOTE_NONE)

        for row in reader:
            text1 = int(row[0])
            text2 = int(row[1])
            value = float(row[2])

            values['texts1'].append(text1)
            values['texts2'].append(text2)
            values['NVS'].append(value)
            # values[(text1, text2)] = value

    # Write pickle file
    print("Going to write pickle file")
    pickle.dump(values, open(out_file, 'wb'))


if __name__ == "__main__":
    main()
