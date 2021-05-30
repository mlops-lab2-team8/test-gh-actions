FROM gcr.io/kf-feast/spark-py:v3.0.1

# REMOVES python2
RUN apt purge python2 -y
RUN apt remove python2.7 -y
RUN apt autoremove -y
RUN update-alternatives --install /usr/bin/python python /usr/bin/python3 0

RUN python --version
