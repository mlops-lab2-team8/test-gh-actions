from flask import Flask, render_template, request, redirect
from fuzzywuzzy import fuzz
from fuzzywuzzy import process
import feast
import os

app = Flask(__name__)

FEAST_URL = os.getenv("FEAST_URL", "izac-feast-core:6565")

c = feast.Client(core_url=FEAST_URL)

@app.route('/', methods=['GET', 'POST'])
def search():
    if request.method == "POST":
        all_data = []
        for i in c.list_feature_tables()[0].features:
            all_data.append(i.name)
        feature = request.form['feature']
        # search by feature
        data = []
        data = process.extractBests(feature, all_data, score_cutoff=50, limit=len(all_data))
        data = [a_tuple[0] for a_tuple in data]
        # all in the search box will return all the features
        if len(data) == 0 and feature == 'all':
            data = all_data
        return render_template('search.html', data=data)
    return render_template('search.html')

if __name__ == '__main__':
    app.run()